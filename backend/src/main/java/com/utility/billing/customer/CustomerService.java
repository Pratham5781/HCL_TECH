package com.utility.billing.customer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.utility.billing.notification.NotificationService;

@Service
public class CustomerService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final Map<String,String> MIME = Map.of("application/pdf",".pdf","image/jpeg",".jpg","image/png",".png");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final java.nio.file.Path uploads;
    private final NotificationService notifications;

    public CustomerService(JdbcTemplate jdbc,ObjectMapper json,NotificationService notifications,@org.springframework.beans.factory.annotation.Value("${app.upload-dir:./uploads}") String uploadDir){
        this.jdbc=jdbc;this.json=json;this.notifications=notifications;this.uploads=java.nio.file.Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Map<String,Object> dashboard(String email){
        long customer=customerId(email);Map<String,Object> out=new LinkedHashMap<>();
        out.put("profile",jdbc.queryForMap("SELECT cp.customer_number customerNumber,cp.full_name fullName,cp.phone,u.email FROM customer_profile cp JOIN users u ON u.id=cp.user_id WHERE cp.user_id=?",customer));
        out.put("connections",jdbc.queryForList("SELECT c.id connectionId,c.meter_number meterNumber,c.address,c.status,s.name service,ct.name connectionType FROM connection c JOIN utility_service s ON s.id=c.service_id JOIN connection_type ct ON ct.id=c.connection_type_id WHERE c.customer_id=? ORDER BY s.name,c.id",customer));
        out.put("totalDue",jdbc.queryForObject("SELECT COALESCE(SUM(GREATEST(b.total_amount-b.paid_amount,0)),0) FROM bill b JOIN connection c ON c.id=b.connection_id WHERE c.customer_id=? AND b.status NOT IN ('PAID','CANCELLED')",BigDecimal.class,customer));
        out.put("openTickets",jdbc.queryForObject("SELECT COUNT(*) FROM ticket WHERE customer_id=? AND status NOT IN ('CLOSED','RESOLVED')",Long.class,customer));
        out.put("recentPayments",jdbc.queryForList("SELECT p.id,p.transaction_id transactionId,p.amount,p.method,p.status,p.paid_at paidAt,b.bill_number billNumber,r.receipt_number receiptNumber FROM payment p JOIN bill b ON b.id=p.bill_id JOIN connection c ON c.id=b.connection_id LEFT JOIN receipt r ON r.payment_id=p.id WHERE c.customer_id=? ORDER BY p.created_at DESC LIMIT 8",customer));
        out.put("monthlyConsumption",jdbc.queryForList("SELECT s.name service,c.meter_number meterNumber,mr.cycle,SUM(mr.consumption) consumption FROM meter_reading mr JOIN connection c ON c.id=mr.connection_id JOIN utility_service s ON s.id=c.service_id WHERE c.customer_id=? GROUP BY s.id,s.name,c.id,c.meter_number,mr.cycle ORDER BY mr.cycle",customer));
        return out;
    }

    public PageResult bills(String email,String query,String status,int page,int size){
        long customer=customerId(email);validatePage(page,size);
        String base="SELECT b.id,b.bill_number billNumber,b.cycle,b.issue_date issueDate,b.due_date dueDate,b.energy_charge energyCharge,b.fixed_charge fixedCharge,b.tax_amount taxAmount,b.arrears,b.late_fee lateFee,b.adjustments,b.total_amount totalAmount,b.paid_amount paidAmount,GREATEST(b.total_amount-b.paid_amount,0) balance,b.status,c.id connectionId,c.meter_number meterNumber,s.name service FROM bill b JOIN connection c ON c.id=b.connection_id JOIN utility_service s ON s.id=c.service_id WHERE c.customer_id=?";
        List<Object> args=new ArrayList<>(List.of(customer));String where="";
        if(query!=null&&!query.isBlank()){where+=" AND LOWER(CONCAT_WS(' ',b.bill_number,b.cycle,c.meter_number,s.name)) LIKE ?";args.add("%"+query.trim().toLowerCase(Locale.ROOT)+"%");}
        if(status!=null&&!status.isBlank()){String normalized=status.toUpperCase(Locale.ROOT);if(!List.of("GENERATED","PARTIALLY_PAID","PAID","OVERDUE","CANCELLED").contains(normalized))throw bad("Unsupported bill status");where+=" AND b.status=?";args.add(normalized);}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM bill b JOIN connection c ON c.id=b.connection_id JOIN utility_service s ON s.id=c.service_id WHERE c.customer_id=?"+where,args.toArray(),Long.class);
        List<Object> paged=new ArrayList<>(args);paged.add(size);paged.add(page*size);
        return new PageResult(jdbc.queryForList(base+where+" ORDER BY b.issue_date DESC,b.id DESC LIMIT ? OFFSET ?",paged.toArray()),page,size,total);
    }

    public Map<String,Object> billDetail(String email,long id){long customer=customerId(email);assertBillOwner(customer,id);Map<String,Object> out=jdbc.queryForMap("SELECT b.id,b.bill_number billNumber,b.cycle,b.issue_date issueDate,b.due_date dueDate,b.energy_charge energyCharge,b.fixed_charge fixedCharge,b.tax_amount taxAmount,b.arrears,b.late_fee lateFee,b.adjustments,b.total_amount totalAmount,b.paid_amount paidAmount,GREATEST(b.total_amount-b.paid_amount,0) balance,b.status,c.meter_number meterNumber,s.name service,cp.customer_number customerNumber,cp.full_name customerName FROM bill b JOIN connection c ON c.id=b.connection_id JOIN utility_service s ON s.id=c.service_id JOIN customer_profile cp ON cp.user_id=c.customer_id WHERE b.id=?",id);out.put("items",jdbc.queryForList("SELECT description,quantity,unit_rate unitRate,amount FROM bill_item WHERE bill_id=? ORDER BY id",id));return out;}

    @SuppressWarnings("unchecked") public byte[] billPdf(String email,long id){Map<String,Object> bill=billDetail(email,id);List<Map<String,Object>> items=(List<Map<String,Object>>)bill.get("items");return pdf("UtilityCare Bill",bill,items,"Bill Number","billNumber");}

    public PageResult readings(String email,Long connectionId,String cycle,int page,int size){long customer=customerId(email);validatePage(page,size);String where=" WHERE c.customer_id=?";List<Object> args=new ArrayList<>(List.of(customer));if(connectionId!=null){where+=" AND c.id=?";args.add(connectionId);}if(cycle!=null&&!cycle.isBlank()){where+=" AND mr.cycle=?";args.add(cycle);}String base="SELECT mr.id,mr.cycle,mr.reading_date readingDate,mr.previous_reading previousReading,mr.current_reading currentReading,mr.consumption,mr.rollover,mr.meter_reset meterReset,c.meter_number meterNumber,s.name service FROM meter_reading mr JOIN connection c ON c.id=mr.connection_id JOIN utility_service s ON s.id=c.service_id";long total=jdbc.queryForObject("SELECT COUNT(*) FROM meter_reading mr JOIN connection c ON c.id=mr.connection_id"+where,args.toArray(),Long.class);List<Object> paged=new ArrayList<>(args);paged.add(size);paged.add(page*size);return new PageResult(jdbc.queryForList(base+where+" ORDER BY mr.reading_date DESC,mr.id DESC LIMIT ? OFFSET ?",paged.toArray()),page,size,total);}

    public PageResult payments(String email,int page,int size){long customer=customerId(email);validatePage(page,size);String base="SELECT p.id,p.transaction_id transactionId,p.amount,p.method,p.status,p.failure_reason failureReason,p.paid_at paidAt,b.bill_number billNumber,b.id billId,r.receipt_number receiptNumber FROM payment p JOIN bill b ON b.id=p.bill_id JOIN connection c ON c.id=b.connection_id LEFT JOIN receipt r ON r.payment_id=p.id WHERE c.customer_id=?";long total=jdbc.queryForObject("SELECT COUNT(*) FROM payment p JOIN bill b ON b.id=p.bill_id JOIN connection c ON c.id=b.connection_id WHERE c.customer_id=?",Long.class,customer);return new PageResult(jdbc.queryForList(base+" ORDER BY p.created_at DESC LIMIT ? OFFSET ?",customer,size,page*size),page,size,total);}

    @Transactional
    public Map<String,Object> pay(String email,long billId,BigDecimal requested,String method,boolean simulateSuccess){
        long customer=customerId(email);assertBillOwner(customer,billId);String normalized=method==null?"":method.toUpperCase(Locale.ROOT);if(!List.of("UPI","CARD","NETBANKING").contains(normalized))throw bad("Choose UPI, CARD or NETBANKING");
        Map<String,Object> bill=jdbc.queryForMap("SELECT id,bill_number billNumber,total_amount totalAmount,paid_amount paidAmount,status,version FROM bill WHERE id=? FOR UPDATE",billId);
        BigDecimal remaining=((BigDecimal)bill.get("totalAmount")).subtract((BigDecimal)bill.get("paidAmount")).max(ZERO).setScale(2,RoundingMode.HALF_UP);
        if(remaining.signum()<=0||List.of("CANCELLED","PAID").contains(bill.get("status")))throw conflict("This bill has no payable balance");
        BigDecimal amount=requested==null?remaining:requested.setScale(2,RoundingMode.HALF_UP);if(amount.signum()<=0||amount.compareTo(remaining)>0)throw bad("Payment must be greater than zero and no more than the outstanding balance");
        String transaction="TXN-"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase(Locale.ROOT);String status=simulateSuccess?"SUCCESS":"FAILED";String failure=simulateSuccess?null:"Mock gateway declined the transaction";
        jdbc.update("INSERT INTO payment(bill_id,transaction_id,amount,method,status,failure_reason,paid_at) VALUES (?,?,?,?,?,?,IF(?='SUCCESS',UTC_TIMESTAMP(6),NULL))",billId,transaction,amount,normalized,status,failure,status);
        Long paymentId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        if(simulateSuccess){BigDecimal paid=((BigDecimal)bill.get("paidAmount")).add(amount).setScale(2,RoundingMode.HALF_UP);String next=paid.compareTo((BigDecimal)bill.get("totalAmount"))>=0?"PAID":"PARTIALLY_PAID";int changed=jdbc.update("UPDATE bill SET paid_amount=?,status=?,version=version+1 WHERE id=? AND version=?",paid,next,billId,bill.get("version"));if(changed!=1)throw conflict("Bill changed while processing payment; retry");String receipt="RCT-"+UUID.randomUUID().toString().replace("-","").substring(0,18).toUpperCase(Locale.ROOT);jdbc.update("INSERT INTO receipt(payment_id,receipt_number) VALUES (?,?)",paymentId,receipt);notifications.notify(customer,"Payment received","Your payment of Rs. "+amount+" for bill "+bill.get("billNumber")+" was successful.","PAYMENT_"+paymentId,true);}
        Map<String,Object> out=new LinkedHashMap<>();out.put("paymentId",paymentId);out.put("transactionId",transaction);out.put("amount",amount);out.put("method",normalized);out.put("status",status);out.put("failureReason",failure);out.put("remainingBalance",simulateSuccess?remaining.subtract(amount):remaining);out.put("receiptNumber",simulateSuccess?jdbc.queryForObject("SELECT receipt_number FROM receipt WHERE payment_id=?",String.class,paymentId):null);return out;
    }

    public byte[] receiptPdf(String email,long paymentId){long customer=customerId(email);Map<String,Object> data=jdbc.queryForMap("SELECT r.receipt_number receiptNumber,p.transaction_id transactionId,p.amount,p.method,p.status,p.paid_at paidAt,b.bill_number billNumber,b.cycle,cp.customer_number customerNumber,cp.full_name customerName FROM receipt r JOIN payment p ON p.id=r.payment_id JOIN bill b ON b.id=p.bill_id JOIN connection c ON c.id=b.connection_id JOIN customer_profile cp ON cp.user_id=c.customer_id WHERE p.id=? AND c.customer_id=?",paymentId,customer);return pdf("UtilityCare Payment Receipt",data,List.of(),"Receipt Number","receiptNumber");}

    public List<Map<String,Object>> services(){return jdbc.queryForList("SELECT id,code,name FROM utility_service WHERE active=TRUE ORDER BY name");}
    public PageResult requests(String email,int page,int size){long customer=customerId(email);validatePage(page,size);String base="SELECT r.id,r.request_type requestType,r.status,r.request_status requestStatus,r.desired_address desiredAddress,r.customer_notes customerNotes,r.created_at createdAt,r.document_path documentPath,s.name service FROM connection_request r JOIN utility_service s ON s.id=r.service_id WHERE r.customer_id=?";long total=jdbc.queryForObject("SELECT COUNT(*) FROM connection_request WHERE customer_id=?",Long.class,customer);return new PageResult(jdbc.queryForList(base+" ORDER BY r.created_at DESC LIMIT ? OFFSET ?",customer,size,page*size),page,size,total);}

    @Transactional
    public Map<String,Object> createRequest(String email,RequestInput input,MultipartFile document){long customer=customerId(email);if(!List.of("NEW_CONNECTION","DISCONNECTION","METER_CHANGE").contains(input.requestType()))throw bad("Unsupported connection request type");Integer service=jdbc.queryForObject("SELECT COUNT(*) FROM utility_service WHERE id=? AND active=TRUE",Integer.class,input.serviceId());if(service==null||service==0)throw bad("Choose an active utility service");String path=storeDocument(document);Map<String,Object> details=new LinkedHashMap<>();details.put("desiredAddress",input.desiredAddress());details.put("notes",input.notes());try{jdbc.update("INSERT INTO connection_request(customer_id,service_id,request_type,details,document_path,desired_address,customer_notes,status,request_status) VALUES (?,?,?,CAST(? AS JSON),?,?,?,'PENDING','PENDING')",customer,input.serviceId(),input.requestType(),json.writeValueAsString(details),path,input.desiredAddress(),input.notes());}catch(JsonProcessingException e){throw new IllegalStateException("Could not save connection request",e);}Long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);return jdbc.queryForMap("SELECT r.id,r.request_type requestType,r.status,r.request_status requestStatus,r.desired_address desiredAddress,r.customer_notes customerNotes,r.created_at createdAt,s.name service FROM connection_request r JOIN utility_service s ON s.id=r.service_id WHERE r.id=?",id);}

    public StoredFile requestDocument(String email,long id){long customer=customerId(email);List<String> paths=jdbc.query("SELECT document_path FROM connection_request WHERE id=? AND customer_id=?",(rs,row)->rs.getString(1),id,customer);if(paths.isEmpty()||paths.get(0)==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Attachment not found");return readFile(paths.get(0));}

    public Map<String,Object> updateProfile(String email,String phone){long customer=customerId(email);jdbc.update("UPDATE customer_profile SET phone=? WHERE user_id=?",phone,customer);return jdbc.queryForMap("SELECT customer_number customerNumber,full_name fullName,phone FROM customer_profile WHERE user_id=?",customer);}

    private byte[] pdf(String title,Map<String,Object> data,List<Map<String,Object>> items,String numberLabel,String numberKey){try(ByteArrayOutputStream bytes=new ByteArrayOutputStream()){Document doc=new Document();PdfWriter.getInstance(doc,bytes);doc.open();Font heading=new Font(Font.HELVETICA,18,Font.BOLD);doc.add(new Paragraph(title,heading));doc.add(new Paragraph("UtilityCare | Customer billing portal"));doc.add(new Paragraph(numberLabel+": "+data.get(numberKey)));for(String key:List.of("customerName","customerNumber","billNumber","cycle","service","issueDate","dueDate","paidAt","transactionId","method","amount","totalAmount","paidAmount","balance","status")){if(data.containsKey(key)&&data.get(key)!=null)doc.add(new Paragraph(label(key)+": "+data.get(key)));}if(!items.isEmpty()){doc.add(new Paragraph(" "));PdfPTable table=new PdfPTable(new float[]{5,1.2f,1.5f,1.5f});table.setWidthPercentage(100);for(String h:List.of("Description","Quantity","Rate","Amount")){PdfPCell cell=new PdfPCell(new Phrase(h));cell.setPadding(6);table.addCell(cell);}for(Map<String,Object> item:items){table.addCell(item.get("description").toString());table.addCell(String.valueOf(item.get("quantity")));table.addCell(String.valueOf(item.get("unitRate")));table.addCell(String.valueOf(item.get("amount")));}doc.add(table);}doc.close();return bytes.toByteArray();}catch(Exception e){throw new IllegalStateException("Could not create PDF document",e);}}
    private String label(String key){return switch(key){case "customerName"->"Customer";case "customerNumber"->"Customer ID";case "billNumber"->"Bill";case "dueDate"->"Due date";case "paidAt"->"Paid at (UTC)";case "totalAmount"->"Bill total";case "paidAmount"->"Paid";case "transactionId"->"Transaction";default->key;};}
    private String storeDocument(MultipartFile file){if(file==null||file.isEmpty())return null;if(file.getSize()>10*1024*1024)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Attachment must not exceed 10 MB");String ext=MIME.get(file.getContentType()==null?"":file.getContentType().toLowerCase(Locale.ROOT));if(ext==null)throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Attachment must be PDF, JPEG or PNG");try{java.nio.file.Files.createDirectories(uploads);String name=UUID.randomUUID()+ext;java.nio.file.Path target=uploads.resolve(name).normalize();if(!target.startsWith(uploads))throw bad("Invalid attachment path");file.transferTo(target);return name;}catch(java.io.IOException e){throw new IllegalStateException("Could not store attachment",e);}}
    private StoredFile readFile(String name){if(name==null||!name.matches("[0-9a-fA-F-]{36}\\.(pdf|jpg|png)"))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Attachment not found");java.nio.file.Path path=uploads.resolve(name).normalize();if(!path.startsWith(uploads)||!java.nio.file.Files.isRegularFile(path))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Attachment not found");try{return new StoredFile(name,java.nio.file.Files.readAllBytes(path),name.endsWith(".pdf")?"application/pdf":name.endsWith(".png")?"image/png":"image/jpeg");}catch(java.io.IOException e){throw new IllegalStateException("Could not read attachment",e);}}
    private long customerId(String email){try{return jdbc.queryForObject("SELECT cp.user_id FROM customer_profile cp JOIN users u ON u.id=cp.user_id WHERE u.email=? AND u.role='CUSTOMER' AND u.status='ACTIVE' AND cp.approval_status='APPROVED' AND u.deleted_at IS NULL",Long.class,email);}catch(org.springframework.dao.EmptyResultDataAccessException e){throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Customer account is not approved");}}
    private void assertBillOwner(long customer,long bill){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM bill b JOIN connection c ON c.id=b.connection_id WHERE b.id=? AND c.customer_id=?",Integer.class,bill,customer);if(n==null||n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Bill not found");}
    private void validatePage(int page,int size){if(page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE)throw bad("Page must be non-negative and size must be between 1 and 100");}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}private ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    public record PageResult(List<Map<String,Object>> items,int page,int size,long total){}
    public record StoredFile(String name,byte[] bytes,String mediaType){}
    public record RequestInput(long serviceId,String requestType,String desiredAddress,String notes){}
}
