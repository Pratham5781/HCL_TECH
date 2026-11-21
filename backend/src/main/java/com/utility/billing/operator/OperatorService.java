package com.utility.billing.operator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import com.utility.billing.notification.NotificationService;

@Service
public class OperatorService {
    private final JdbcTemplate jdbc;
    private final BillingService billing;
    private final PhotoStorageService photos;
    private final ObjectMapper json;
    private final NotificationService notifications;

    public OperatorService(JdbcTemplate jdbc,BillingService billing,PhotoStorageService photos,ObjectMapper json,NotificationService notifications){this.jdbc=jdbc;this.billing=billing;this.photos=photos;this.json=json;this.notifications=notifications;}

    public PageResult customers(String actor,String query,int page,int size,String sort,String direction){
        long operatorId=actorId(actor);
        String from="SELECT DISTINCT c.id connectionId,c.customer_id customerId,cp.customer_number customerNumber,cp.full_name fullName,cp.phone,c.meter_number meterNumber,c.address,c.status connectionStatus,s.name service,z.name zone FROM connection c JOIN customer_profile cp ON cp.user_id=c.customer_id JOIN utility_service s ON s.id=c.service_id LEFT JOIN zone z ON z.id=c.zone_id";
        String scope="(EXISTS(SELECT 1 FROM operator_customer oc WHERE oc.operator_id=? AND oc.customer_id=c.customer_id) OR EXISTS(SELECT 1 FROM operator_zone oz WHERE oz.operator_id=? AND oz.zone_id=c.zone_id))";
        String search="LOWER(CONCAT_WS(' ',cp.customer_number,cp.full_name,cp.phone,c.meter_number,c.address,s.name,z.name))";
        String where=" WHERE "+scope;List<Object> args=new ArrayList<>(List.of(operatorId,operatorId));
        if(query!=null&&!query.isBlank()){where+=" AND "+search+" LIKE ?";args.add("%"+query.trim().toLowerCase(Locale.ROOT)+"%");}
        Map<String,String> sorts=Map.of("customerNumber","cp.customer_number","fullName","cp.full_name","meterNumber","c.meter_number","service","s.name","zone","z.name");
        return page(from,where,args,page,size,sort,direction,sorts,"fullName");
    }

    public List<Map<String,Object>> billingConnections(String actor,String cycle){
        actorId(actor);
        return jdbc.queryForList("SELECT c.id connectionId,c.customer_id customerId,cp.customer_number customerNumber,cp.full_name customerName,c.meter_number meterNumber,c.status connectionStatus,s.name service FROM connection c JOIN customer_profile cp ON cp.user_id=c.customer_id JOIN users u ON u.id=cp.user_id JOIN utility_service s ON s.id=c.service_id WHERE u.status='ACTIVE' AND u.deleted_at IS NULL AND cp.approval_status='APPROVED' AND c.status='ACTIVE' AND EXISTS(SELECT 1 FROM meter_reading mr WHERE mr.connection_id=c.id AND mr.cycle=?) AND NOT EXISTS(SELECT 1 FROM bill b WHERE b.connection_id=c.id AND b.cycle=?) ORDER BY cp.full_name,s.name,c.meter_number",cycle,cycle);
    }

    public List<Map<String,Object>> customerConnections(String actor,long customerId){
        long operatorId=actorId(actor);assertAssignedCustomer(operatorId,customerId);
        return jdbc.queryForList("SELECT c.id connectionId,c.meter_number meterNumber,c.address,c.status connectionStatus,s.id serviceId,s.code serviceCode,s.name service,ct.id connectionTypeId,ct.name connectionType,z.name zone FROM connection c JOIN utility_service s ON s.id=c.service_id JOIN connection_type ct ON ct.id=c.connection_type_id LEFT JOIN zone z ON z.id=c.zone_id WHERE c.customer_id=? ORDER BY s.name,c.meter_number",customerId);
    }

    public Map<String,Object> previousReading(String actor,long connectionId){
        long operatorId=actorId(actor);assertAssignedConnection(operatorId,connectionId);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT current_reading currentReading,reading_date readingDate,cycle FROM meter_reading WHERE connection_id=? ORDER BY reading_date DESC,id DESC LIMIT 1",connectionId);
        Map<String,Object> result=new LinkedHashMap<>();result.put("connectionId",connectionId);
        if(rows.isEmpty()){result.put("previousReading",BigDecimal.ZERO);result.put("lastReadingDate",null);result.put("lastCycle",null);}
        else{result.put("previousReading",rows.get(0).get("currentReading"));result.put("lastReadingDate",rows.get(0).get("readingDate"));result.put("lastCycle",rows.get(0).get("cycle"));}
        return result;
    }

    @Transactional
    public Map<String,Object> createReading(ReadingInput input,MultipartFile photo,String actor,String ip){
        long operatorId=actorId(actor);assertAssignedConnection(operatorId,input.connectionId());
        if(input.cycle()==null||!input.cycle().matches("\\d{4}-(0[1-9]|1[0-2])"))throw bad("Cycle must use YYYY-MM format");
        if(!input.readingDate().format(DateTimeFormatter.ofPattern("yyyy-MM")).equals(input.cycle()))throw bad("Reading date must be within the selected billing cycle");
        if(input.currentReading()==null||input.currentReading().signum()<0)throw bad("Current reading must be zero or greater");
        if(input.meterReset()&&input.rollover())throw bad("Choose either meter reset or rollover");
        if((input.rollover()||input.meterReset())&&(input.remarks()==null||input.remarks().isBlank()))throw bad("Meter reset or rollover requires remarks");
        if(input.meterMaximum()!=null&&input.meterMaximum().signum()<0)throw bad("Meter maximum cannot be negative");
        jdbc.queryForObject("SELECT id FROM connection WHERE id=? FOR UPDATE",Long.class,input.connectionId());
        if(jdbc.queryForObject("SELECT COUNT(*) FROM meter_reading WHERE connection_id=? AND cycle=?",Integer.class,input.connectionId(),input.cycle())>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"A meter reading already exists for this connection and cycle");
        Map<String,Object> connection=connection(input.connectionId());
        List<Map<String,Object>> previous=jdbc.queryForList("SELECT current_reading currentReading,reading_date readingDate FROM meter_reading WHERE connection_id=? ORDER BY reading_date DESC,id DESC LIMIT 1",input.connectionId());
        BigDecimal previousValue=previous.isEmpty()?BigDecimal.ZERO:new BigDecimal(previous.get(0).get("currentReading").toString());
        BigDecimal consumption;
        if(input.meterReset())consumption=input.currentReading();
        else if(input.currentReading().compareTo(previousValue)>=0)consumption=input.currentReading().subtract(previousValue);
        else{
            if(!input.rollover()||input.meterMaximum()==null||input.meterMaximum().compareTo(previousValue)<=0)throw bad("Current reading must not be below the previous reading unless a valid rollover is recorded");
            consumption=input.meterMaximum().subtract(previousValue).add(input.currentReading());
        }
        if(consumption.signum()<0)throw bad("Calculated consumption cannot be negative");
        String photoName=photos.store(photo);
        jdbc.update("INSERT INTO meter_reading(connection_id,previous_reading,current_reading,consumption,meter_maximum,reading_date,cycle,rollover,meter_reset,remarks,photo_path,recorded_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                input.connectionId(),previousValue,input.currentReading(),consumption,input.meterMaximum(),input.readingDate(),input.cycle(),input.rollover(),input.meterReset(),input.remarks(),photoName,operatorId);
        Long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        Map<String,Object> after=reading(id);
        audit(actor,"METER_READING_CREATED","meter_reading",id,ip,Map.of("previousReading",previousValue),after);
        return after;
    }

    public PageResult readings(String actor,String query,String cycle,int page,int size,String sort,String direction){
        long operatorId=actorId(actor);
        String base="SELECT mr.id,mr.cycle,mr.previous_reading previousReading,mr.current_reading currentReading,mr.consumption,mr.meter_maximum meterMaximum,mr.reading_date readingDate,mr.rollover,mr.meter_reset meterReset,mr.remarks,mr.photo_path photoPath,c.id connectionId,c.meter_number meterNumber,cp.customer_number customerNumber,cp.full_name customerName,s.name service FROM meter_reading mr JOIN connection c ON c.id=mr.connection_id JOIN customer_profile cp ON cp.user_id=c.customer_id JOIN utility_service s ON s.id=c.service_id";
        String scope="(EXISTS(SELECT 1 FROM operator_customer oc WHERE oc.operator_id=? AND oc.customer_id=c.customer_id) OR EXISTS(SELECT 1 FROM operator_zone oz WHERE oz.operator_id=? AND oz.zone_id=c.zone_id))";
        String where=" WHERE "+scope;List<Object> args=new ArrayList<>(List.of(operatorId,operatorId));
        if(cycle!=null&&!cycle.isBlank()){where+=" AND mr.cycle=?";args.add(cycle);}
        if(query!=null&&!query.isBlank()){where+=" AND LOWER(CONCAT_WS(' ',mr.cycle,c.meter_number,cp.customer_number,cp.full_name,s.name)) LIKE ?";args.add("%"+query.trim().toLowerCase(Locale.ROOT)+"%");}
        return page(base,where,args,page,size,sort,direction,Map.of("id","mr.id","cycle","mr.cycle","readingDate","mr.reading_date","meterNumber","c.meter_number","customerName","cp.full_name"),"readingDate");
    }

    public PageResult bills(String actor,String query,String status,int page,int size,String sort,String direction){
        actorId(actor);
        String base="SELECT b.id,b.bill_number billNumber,b.cycle,b.issue_date issueDate,b.due_date dueDate,b.energy_charge energyCharge,b.fixed_charge fixedCharge,b.tax_amount taxAmount,b.arrears,b.late_fee lateFee,b.adjustments,b.total_amount totalAmount,b.paid_amount paidAmount,b.status,c.id connectionId,c.meter_number meterNumber,cp.customer_number customerNumber,cp.full_name customerName,s.name service FROM bill b JOIN connection c ON c.id=b.connection_id JOIN customer_profile cp ON cp.user_id=c.customer_id JOIN utility_service s ON s.id=c.service_id";
        String where=" WHERE 1=1";List<Object> args=new ArrayList<>();
        if(status!=null&&!status.isBlank()){where+=" AND b.status=?";args.add(status.toUpperCase(Locale.ROOT));}
        if(query!=null&&!query.isBlank()){where+=" AND LOWER(CONCAT_WS(' ',b.bill_number,b.cycle,b.status,c.meter_number,cp.customer_number,cp.full_name,s.name)) LIKE ?";args.add("%"+query.trim().toLowerCase(Locale.ROOT)+"%");}
        return page(base,where,args,page,size,sort,direction,Map.of("id","b.id","billNumber","b.bill_number","cycle","b.cycle","dueDate","b.due_date","totalAmount","b.total_amount","status","b.status","customerName","cp.full_name"),"dueDate");
    }

    @Transactional
    public Map<String,Object> generateBill(long connectionId,String cycle,BigDecimal adjustments,String actor,String ip){
        long operatorId=actorId(actor);assertBillableConnection(connectionId);
        if(cycle==null||!cycle.matches("\\d{4}-(0[1-9]|1[0-2])"))throw bad("Cycle must use YYYY-MM format");
        return createBillForReading(connectionId,cycle,adjustments==null?BigDecimal.ZERO:adjustments,operatorId,actor,ip);
    }

    @Transactional
    public Map<String,Object> regenerateBill(long billId,BigDecimal adjustments,String actor,String ip){
        long operatorId=actorId(actor);
        Map<String,Object> old=bill(billId);long connectionId=((Number)old.get("connectionId")).longValue();assertBillableConnection(connectionId);
        BigDecimal paid=new BigDecimal(old.get("paidAmount").toString());
        if(paid.signum()>0||"PAID".equals(old.get("status"))||"CANCELLED".equals(old.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"A bill cannot be edited after payment or cancellation");
        BigDecimal adj=adjustments==null?new BigDecimal(old.get("adjustments").toString()):adjustments;
        Map<String,Object> quote=quoteForReading(connectionId,old.get("cycle").toString(),adj);
        int updated=jdbc.update("UPDATE bill SET issue_date=?,due_date=?,energy_charge=?,fixed_charge=?,tax_amount=?,arrears=?,late_fee=?,adjustments=?,total_amount=?,status='GENERATED' WHERE id=? AND paid_amount=0",
                LocalDate.now(ZoneOffset.UTC),quote.get("dueDate"),quote.get("energyCharge"),quote.get("fixedCharge"),quote.get("taxAmount"),quote.get("arrears"),quote.get("lateFee"),quote.get("adjustments"),quote.get("totalAmount"),billId);
        if(updated==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"The bill changed or received a payment while it was being edited");
        jdbc.update("DELETE FROM bill_item WHERE bill_id=?",billId);addBillItems(billId,quote,connectionId,old.get("cycle").toString());
        Map<String,Object> next=bill(billId);audit(actor,"BILL_REGENERATED","bill",billId,ip,old,next);return next;
    }

    @Transactional
    public Map<String,Object> bulkGenerate(String cycle,String actor,String ip){
        long operatorId=actorId(actor);
        if(cycle==null||!cycle.matches("\\d{4}-(0[1-9]|1[0-2])"))throw bad("Cycle must use YYYY-MM format");
        String query="SELECT mr.connection_id FROM meter_reading mr JOIN connection c ON c.id=mr.connection_id JOIN customer_profile cp ON cp.user_id=c.customer_id JOIN users u ON u.id=cp.user_id WHERE mr.cycle=? AND c.status='ACTIVE' AND u.status='ACTIVE' AND u.deleted_at IS NULL AND cp.approval_status='APPROVED' AND NOT EXISTS(SELECT 1 FROM bill b WHERE b.connection_id=mr.connection_id AND b.cycle=mr.cycle) ORDER BY mr.connection_id";
        List<Long> connections=jdbc.queryForList(query,Long.class,cycle);
        int created=0;
        for(Long connectionId:connections){createBillForReading(connectionId,cycle,BigDecimal.ZERO,operatorId,actor,ip);created++;}
        Map<String,Object> result=new LinkedHashMap<>();result.put("cycle",cycle);result.put("billsCreated",created);return result;
    }

    @Transactional
    public Map<String,Object> createLpgBooking(LpgBookingInput input,String actor,String ip){
        long operatorId=actorId(actor);assertAssignedConnection(operatorId,input.connectionId());
        Map<String,Object> connection=connection(input.connectionId());
        if(!"LPG".equals(connection.get("serviceCode")))throw bad("Cylinder bookings require an LPG connection");
        LocalDate issue=LocalDate.now(ZoneOffset.UTC);String cycle=issue.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        if(jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE connection_id=? AND cycle=?",Integer.class,input.connectionId(),cycle)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"This connection already has a bill for the current billing cycle");
        List<BillingService.TariffRate> slabs=tariffs(((Number)connection.get("serviceId")).longValue(),((Number)connection.get("connectionTypeId")).longValue(),issue);
        if(slabs.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"No effective LPG tariff is configured");
        BigDecimal unitPrice=slabs.get(0).ratePerUnit();
        BillingService.BillQuote quote=billing.calculateLpg(input.cylinderCount(),unitPrice,input.subsidy(),input.deliveryCharge(),issue,slabs.get(0).dueDays());
        String bookingNo="LPG-"+UUID.randomUUID();
        String billNo="BL-"+UUID.randomUUID();
        jdbc.update("INSERT INTO bill(bill_number,connection_id,reading_id,cycle,issue_date,due_date,energy_charge,fixed_charge,tax_amount,arrears,late_fee,adjustments,total_amount,status) VALUES (?,?,NULL,?,?,?,?,?,0,0,0,?,?, 'GENERATED')",
                billNo,input.connectionId(),cycle,issue,quote.dueDate(),quote.energyCharge(),quote.fixedCharge(),quote.adjustments(),quote.totalAmount());
        Long billId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        addLpgBillItems(billId,input.cylinderCount(),unitPrice,input.subsidy(),input.deliveryCharge());
        jdbc.update("INSERT INTO lpg_booking(booking_number,connection_id,cylinder_count,unit_price,subsidy,delivery_charge,status,bill_id,booked_by) VALUES (?,?,?,?,?,?,'BOOKED',?,?)",
                bookingNo,input.connectionId(),input.cylinderCount(),unitPrice,input.subsidy(),input.deliveryCharge(),billId,operatorId);
        Long bookingId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        Map<String,Object> result=booking(bookingId);
        audit(actor,"LPG_BOOKING_CREATED","lpg_booking",bookingId,ip,null,result);return result;
    }

    public PageResult lpgBookings(String actor,String query,String status,int page,int size){
        long operatorId=actorId(actor);
        String base="SELECT lb.id,lb.booking_number bookingNumber,lb.cylinder_count cylinderCount,lb.unit_price unitPrice,lb.subsidy,lb.delivery_charge deliveryCharge,lb.status,lb.booked_at bookedAt,lb.delivered_at deliveredAt,b.bill_number billNumber,b.total_amount totalAmount,c.id connectionId,c.meter_number meterNumber,cp.customer_number customerNumber,cp.full_name customerName FROM lpg_booking lb JOIN connection c ON c.id=lb.connection_id JOIN customer_profile cp ON cp.user_id=c.customer_id LEFT JOIN bill b ON b.id=lb.bill_id";
        String where=" WHERE (EXISTS(SELECT 1 FROM operator_customer oc WHERE oc.operator_id=? AND oc.customer_id=c.customer_id) OR EXISTS(SELECT 1 FROM operator_zone oz WHERE oz.operator_id=? AND oz.zone_id=c.zone_id))";List<Object> args=new ArrayList<>(List.of(operatorId,operatorId));
        if(status!=null&&!status.isBlank()){where+=" AND lb.status=?";args.add(status.toUpperCase(Locale.ROOT));}
        if(query!=null&&!query.isBlank()){where+=" AND LOWER(CONCAT_WS(' ',lb.booking_number,lb.status,c.meter_number,cp.customer_number,cp.full_name,b.bill_number)) LIKE ?";args.add("%"+query.trim().toLowerCase(Locale.ROOT)+"%");}
        return page(base,where,args,page,size,"bookedAt","DESC",Map.of("id","lb.id","bookingNumber","lb.booking_number","status","lb.status","bookedAt","lb.booked_at"),"bookedAt");
    }

    @Transactional
    public Map<String,Object> updateLpgStatus(long bookingId,String status,String actor,String ip){
        long operatorId=actorId(actor);Map<String,Object> old=booking(bookingId);long connectionId=((Number)old.get("connectionId")).longValue();assertAssignedConnection(operatorId,connectionId);
        String current=old.get("status").toString(),next=status.toUpperCase(Locale.ROOT);
        boolean allowed=switch(current){case "BOOKED"->List.of("REFILLED","CANCELLED").contains(next);case "REFILLED"->List.of("OUT_FOR_DELIVERY","CANCELLED").contains(next);case "OUT_FOR_DELIVERY"->"DELIVERED".equals(next);default->false;};
        if(!allowed)throw new ResponseStatusException(HttpStatus.CONFLICT,"LPG booking status transition is not allowed");
        if("CANCELLED".equals(next)&&old.get("billId")!=null){int cancelled=jdbc.update("UPDATE bill SET status='CANCELLED' WHERE id=? AND paid_amount=0",old.get("billId"));if(cancelled==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"A paid LPG bill cannot be cancelled");}
        jdbc.update("UPDATE lpg_booking SET status=?,delivered_at=CASE WHEN ?='DELIVERED' THEN UTC_TIMESTAMP(6) ELSE delivered_at END WHERE id=?",next,next,bookingId);
        Map<String,Object> nextRecord=booking(bookingId);audit(actor,"LPG_BOOKING_STATUS_CHANGED","lpg_booking",bookingId,ip,old,nextRecord);return nextRecord;
    }

    public Map<String,Object> readingPhoto(long readingId,String actor){
        long operatorId=actorId(actor);
        Map<String,Object> row=jdbc.queryForMap("SELECT connection_id connectionId,photo_path photoPath FROM meter_reading WHERE id=?",readingId);
        assertAssignedConnection(operatorId,((Number)row.get("connectionId")).longValue());
        if(row.get("photoPath")==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No photo was attached to this reading");
        String name=row.get("photoPath").toString();return Map.of("filename",name,"mediaType",photos.mediaType(name),"bytes",photos.read(name));
    }

    private Map<String,Object> createBillForReading(long connectionId,String cycle,BigDecimal adjustments,long operatorId,String actor,String ip){
        if(jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE connection_id=? AND cycle=?",Integer.class,connectionId,cycle)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"A bill already exists for this connection and cycle");
        Map<String,Object> reading=jdbc.queryForMap("SELECT id,consumption FROM meter_reading WHERE connection_id=? AND cycle=?",connectionId,cycle);
        Map<String,Object> quote=quoteForReading(connectionId,cycle,adjustments);
        String billNo="BL-"+UUID.randomUUID();LocalDate issue=LocalDate.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO bill(bill_number,connection_id,reading_id,cycle,issue_date,due_date,energy_charge,fixed_charge,tax_amount,arrears,late_fee,adjustments,total_amount,status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'GENERATED')",
                billNo,connectionId,reading.get("id"),cycle,issue,quote.get("dueDate"),quote.get("energyCharge"),quote.get("fixedCharge"),quote.get("taxAmount"),quote.get("arrears"),quote.get("lateFee"),quote.get("adjustments"),quote.get("totalAmount"));
        Long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        addBillItems(id,quote,connectionId,cycle);
        Map<String,Object> result=bill(id);audit(actor,"BILL_GENERATED","bill",id,ip,null,result);long customerId=((Number)connection(connectionId).get("customerId")).longValue();notifications.notify(customerId,"New utility bill","Bill "+result.get("billNumber")+" for cycle "+cycle+" is ready. Due date: "+result.get("dueDate"),"BILL_GENERATED_"+id,true);return result;
    }

    private Map<String,Object> quoteForReading(long connectionId,String cycle,BigDecimal adjustments){
        Map<String,Object> c=connection(connectionId);
        Map<String,Object> reading=jdbc.queryForMap("SELECT consumption FROM meter_reading WHERE connection_id=? AND cycle=?",connectionId,cycle);
        List<BillingService.TariffRate> slabs=tariffs(((Number)c.get("serviceId")).longValue(),((Number)c.get("connectionTypeId")).longValue(),LocalDate.now(ZoneOffset.UTC));
        BigDecimal arrears=jdbc.queryForObject("SELECT COALESCE(SUM(total_amount-paid_amount),0) FROM bill WHERE connection_id=? AND cycle<>? AND status NOT IN ('PAID','CANCELLED')",BigDecimal.class,connectionId,cycle);
        BillingService.BillQuote quote=billing.calculate(new BigDecimal(reading.get("consumption").toString()),slabs,arrears==null?BigDecimal.ZERO:arrears,adjustments,LocalDate.now(ZoneOffset.UTC));
        Map<String,Object> result=new LinkedHashMap<>();result.put("energyCharge",quote.energyCharge());result.put("fixedCharge",quote.fixedCharge());result.put("taxAmount",quote.taxAmount());result.put("arrears",quote.arrears());result.put("lateFee",quote.lateFee());result.put("adjustments",quote.adjustments());result.put("totalAmount",quote.totalAmount());result.put("dueDate",quote.dueDate());return result;
    }

    private List<BillingService.TariffRate> tariffs(long serviceId,long typeId,LocalDate issue){
        return jdbc.query("SELECT range_start,range_end,rate_per_unit,fixed_charge,tax_percent,late_fee_percent,due_days FROM tariff_slab WHERE service_id=? AND connection_type_id=? AND effective_from<=? AND (effective_to IS NULL OR effective_to>=?) ORDER BY range_start",
                (rs,row)->new BillingService.TariffRate(rs.getBigDecimal("range_start"),rs.getBigDecimal("range_end"),rs.getBigDecimal("rate_per_unit"),rs.getBigDecimal("fixed_charge"),rs.getBigDecimal("tax_percent"),rs.getBigDecimal("late_fee_percent"),rs.getInt("due_days")),serviceId,typeId,issue,issue);
    }

    private void addBillItems(long billId,Map<String,Object> q,long connectionId,String cycle){
        BigDecimal units=jdbc.queryForObject("SELECT consumption FROM meter_reading WHERE connection_id=? AND cycle=?",BigDecimal.class,connectionId,cycle);
        BigDecimal energy=new BigDecimal(q.get("energyCharge").toString());
        jdbc.update("INSERT INTO bill_item(bill_id,description,quantity,unit_rate,amount) VALUES (?,?,?,?,?)",billId,"Slab-wise energy charge",units,units.signum()==0?BigDecimal.ZERO:energy.divide(units,4,java.math.RoundingMode.HALF_UP),energy);
        addItem(billId,"Fixed charge",new BigDecimal(q.get("fixedCharge").toString()));
        addItem(billId,"Tax",new BigDecimal(q.get("taxAmount").toString()));
        addItem(billId,"Previous arrears",new BigDecimal(q.get("arrears").toString()));
        addItem(billId,"Late fee",new BigDecimal(q.get("lateFee").toString()));
        addItem(billId,"Adjustments",new BigDecimal(q.get("adjustments").toString()));
    }
    private void addLpgBillItems(long billId,int count,BigDecimal price,BigDecimal subsidy,BigDecimal delivery){
        jdbc.update("INSERT INTO bill_item(bill_id,description,quantity,unit_rate,amount) VALUES (?,?,?,?,?)",billId,"LPG cylinders",count,price,price.multiply(BigDecimal.valueOf(count)));
        addItem(billId,"Subsidy",subsidy.negate());addItem(billId,"Delivery charge",delivery);
    }
    private void addItem(long billId,String description,BigDecimal amount){if(amount.signum()!=0)jdbc.update("INSERT INTO bill_item(bill_id,description,quantity,unit_rate,amount) VALUES (?,?,1,?,?)",billId,description,amount,amount);}

    private PageResult page(String base,String where,List<Object> args,int page,int size,String sort,String direction,Map<String,String> sorts,String defaultSort){
        if(page<0||size<1||size>100)throw bad("Page must be non-negative and size must be between 1 and 100");
        if((long)page*size>Integer.MAX_VALUE)throw bad("Page number is too large");
        String column=sorts.get(sort==null||sort.isBlank()?defaultSort:sort);if(column==null)throw bad("Unsupported sort field");
        String dir;if("ASC".equalsIgnoreCase(direction))dir="ASC";else if("DESC".equalsIgnoreCase(direction))dir="DESC";else throw bad("Sort direction must be ASC or DESC");
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM ("+base+where+") rows_data",Long.class,args.toArray());
        List<Object> values=new ArrayList<>(args);values.add(size);values.add(page*size);
        return new PageResult(jdbc.queryForList(base+where+" ORDER BY "+column+" "+dir+" LIMIT ? OFFSET ?",values.toArray()),page,size,total);
    }

    private void assertAssignedConnection(long operatorId,long connectionId){
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM connection c WHERE c.id=? AND (EXISTS(SELECT 1 FROM operator_customer oc WHERE oc.operator_id=? AND oc.customer_id=c.customer_id) OR EXISTS(SELECT 1 FROM operator_zone oz WHERE oz.operator_id=? AND oz.zone_id=c.zone_id))",Integer.class,connectionId,operatorId,operatorId);
        if(count==null||count==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Connection is not assigned to this operator");
    }
    private void assertBillableConnection(long connectionId){
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM connection c JOIN customer_profile cp ON cp.user_id=c.customer_id JOIN users u ON u.id=cp.user_id WHERE c.id=? AND c.status='ACTIVE' AND u.status='ACTIVE' AND u.deleted_at IS NULL AND cp.approval_status='APPROVED'",Integer.class,connectionId);
        if(count==null||count==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Connection is not available for billing");
    }
    private void assertAssignedCustomer(long operatorId,long customerId){
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM customer_profile cp WHERE cp.user_id=? AND (EXISTS(SELECT 1 FROM operator_customer oc WHERE oc.operator_id=? AND oc.customer_id=cp.user_id) OR EXISTS(SELECT 1 FROM connection c JOIN operator_zone oz ON oz.zone_id=c.zone_id WHERE c.customer_id=cp.user_id AND oz.operator_id=?))",Integer.class,customerId,operatorId,operatorId);
        if(count==null||count==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Customer is not assigned to this operator");
    }
    private Map<String,Object> connection(long id){
        try{return jdbc.queryForMap("SELECT c.id,c.customer_id customerId,c.service_id serviceId,c.connection_type_id connectionTypeId,s.code serviceCode,c.meter_number meterNumber FROM connection c JOIN utility_service s ON s.id=c.service_id WHERE c.id=?",id);}
        catch(EmptyResultDataAccessException ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Connection not found");}
    }
    private Map<String,Object> reading(long id){return jdbc.queryForMap("SELECT mr.id,mr.connection_id connectionId,mr.previous_reading previousReading,mr.current_reading currentReading,mr.consumption,mr.meter_maximum meterMaximum,mr.reading_date readingDate,mr.cycle,mr.rollover,mr.meter_reset meterReset,mr.remarks,mr.photo_path photoPath,c.meter_number meterNumber,cp.customer_number customerNumber FROM meter_reading mr JOIN connection c ON c.id=mr.connection_id JOIN customer_profile cp ON cp.user_id=c.customer_id WHERE mr.id=?",id);}
    private Map<String,Object> bill(long id){try{return jdbc.queryForMap("SELECT b.id,b.bill_number billNumber,b.connection_id connectionId,b.reading_id readingId,b.cycle,b.issue_date issueDate,b.due_date dueDate,b.energy_charge energyCharge,b.fixed_charge fixedCharge,b.tax_amount taxAmount,b.arrears,b.late_fee lateFee,b.adjustments,b.total_amount totalAmount,b.paid_amount paidAmount,b.status,c.meter_number meterNumber,cp.customer_number customerNumber,cp.full_name customerName FROM bill b JOIN connection c ON c.id=b.connection_id JOIN customer_profile cp ON cp.user_id=c.customer_id WHERE b.id=?",id);}catch(EmptyResultDataAccessException ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Bill not found");}}
    private Map<String,Object> booking(long id){try{return jdbc.queryForMap("SELECT lb.id,lb.booking_number bookingNumber,lb.connection_id connectionId,lb.cylinder_count cylinderCount,lb.unit_price unitPrice,lb.subsidy,lb.delivery_charge deliveryCharge,lb.status,lb.bill_id billId,lb.booked_at bookedAt,lb.delivered_at deliveredAt,b.bill_number billNumber,b.total_amount totalAmount,c.meter_number meterNumber,cp.customer_number customerNumber,cp.full_name customerName FROM lpg_booking lb JOIN connection c ON c.id=lb.connection_id JOIN customer_profile cp ON cp.user_id=c.customer_id LEFT JOIN bill b ON b.id=lb.bill_id WHERE lb.id=?",id);}catch(EmptyResultDataAccessException ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"LPG booking not found");}}
    private long actorId(String email){return jdbc.queryForObject("SELECT id FROM users WHERE email=? AND status='ACTIVE' AND deleted_at IS NULL",Long.class,email);}
    private void audit(String actor,String action,String entity,Object id,String ip,Object oldValue,Object newValue){
        try{jdbc.update("INSERT INTO audit_log(actor_id,action,entity_type,entity_id,ip_address,old_value,new_value) VALUES (?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON))",actorId(actor),action,entity,String.valueOf(id),ip,oldValue==null?null:json.writeValueAsString(oldValue),newValue==null?null:json.writeValueAsString(newValue));}
        catch(JsonProcessingException ex){throw new IllegalStateException("Could not record audit data",ex);}
    }
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    public record PageResult(List<Map<String,Object>> items,int page,int size,long total){}
    public record ReadingInput(long connectionId,BigDecimal currentReading,LocalDate readingDate,String cycle,boolean rollover,boolean meterReset,BigDecimal meterMaximum,String remarks){}
    public record LpgBookingInput(long connectionId,int cylinderCount,BigDecimal subsidy,BigDecimal deliveryCharge){}
}

