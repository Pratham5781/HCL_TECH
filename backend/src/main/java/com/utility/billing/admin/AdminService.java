package com.utility.billing.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.utility.billing.auth.AppUser;
import com.utility.billing.auth.PortalRole;
import com.utility.billing.auth.UserRepository;
import com.utility.billing.notification.NotificationService;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final ObjectMapper json;
    private final NotificationService notifications;

    public AdminService(JdbcTemplate jdbc, UserRepository users, PasswordEncoder passwords, ObjectMapper json,NotificationService notifications) {
        this.jdbc = jdbc; this.users = users; this.passwords = passwords; this.json = json;this.notifications=notifications;
    }

    public Map<String, Object> dashboard() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customers", scalar("SELECT COUNT(*) FROM users WHERE role='CUSTOMER' AND deleted_at IS NULL"));
        result.put("operators", scalar("SELECT COUNT(*) FROM users WHERE role='OPERATOR' AND deleted_at IS NULL"));
        result.put("supportStaff", scalar("SELECT COUNT(*) FROM users WHERE role='HELP_SUPPORT' AND deleted_at IS NULL"));
        result.put("pendingRegistrations", scalar("SELECT COUNT(*) FROM customer_profile WHERE approval_status='PENDING_APPROVAL'"));
        result.put("pendingBills", scalar("SELECT COUNT(*) FROM bill WHERE status IN ('GENERATED','PARTIALLY_PAID') AND due_date>=UTC_DATE() AND total_amount>paid_amount"));
        result.put("overdueBills", scalar("SELECT COUNT(*) FROM bill WHERE status='OVERDUE' OR (due_date<CURRENT_DATE AND total_amount>paid_amount AND status NOT IN ('PAID','CANCELLED'))"));
        result.put("openTickets", scalar("SELECT COUNT(*) FROM ticket WHERE status IN ('OPEN','IN_PROGRESS')"));
        result.put("revenueToday", money("SELECT COALESCE(SUM(amount),0) FROM payment WHERE status='SUCCESS' AND DATE(paid_at)=UTC_DATE()"));
        result.put("revenueMonth", money("SELECT COALESCE(SUM(amount),0) FROM payment WHERE status='SUCCESS' AND YEAR(paid_at)=YEAR(UTC_DATE()) AND MONTH(paid_at)=MONTH(UTC_DATE())"));
        result.put("revenueYear", money("SELECT COALESCE(SUM(amount),0) FROM payment WHERE status='SUCCESS' AND YEAR(paid_at)=YEAR(UTC_DATE())"));
        result.put("consumptionByService", jdbc.queryForList("SELECT us.name service, COALESCE(SUM(mr.consumption),0) consumption FROM utility_service us LEFT JOIN connection c ON c.service_id=us.id LEFT JOIN meter_reading mr ON mr.connection_id=c.id GROUP BY us.id,us.name ORDER BY us.name"));
        result.put("monthlyRevenue", jdbc.queryForList("SELECT DATE_FORMAT(paid_at,'%Y-%m') month, SUM(amount) revenue FROM payment WHERE status='SUCCESS' AND paid_at>=DATE_SUB(UTC_DATE(),INTERVAL 11 MONTH) GROUP BY DATE_FORMAT(paid_at,'%Y-%m') ORDER BY month"));
        return result;
    }

    public PageResult accounts(String query, String role, String status, int page, int size, String sort, String direction) {
        return page("accounts", query, status, role, page, size, sort, direction);
    }

    public PageResult page(String resource, String query, String status, String role, int page, int size, String sort, String direction) {
        if (page < 0 || size < 1 || size > 100) throw bad("Page must be non-negative and size must be between 1 and 100");
        Dataset data = dataset(resource);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (resource.equals("accounts")) where.append(" AND u.deleted_at IS NULL AND u.role<>'ADMIN'");
        if (query != null && !query.isBlank()) { where.append(" AND ").append(data.search()).append(" LIKE ?"); args.add("%" + query.trim().toLowerCase(Locale.ROOT) + "%"); }
        if (status != null && !status.isBlank() && data.status() != null) { where.append(" AND ").append(data.status()).append("=?"); args.add(status.toUpperCase(Locale.ROOT)); }
        if (role != null && !role.isBlank() && resource.equals("accounts")) { where.append(" AND u.role=?"); args.add(role.toUpperCase(Locale.ROOT)); }
        String sortColumn = data.sort().get(sort == null || sort.isBlank() ? data.defaultSort() : sort);
        if (sortColumn == null) throw bad("Unsupported sort field");
        String dir;
        if ("ASC".equalsIgnoreCase(direction)) dir="ASC";
        else if ("DESC".equalsIgnoreCase(direction)) dir="DESC";
        else throw bad("Sort direction must be ASC or DESC");
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + data.sql() + where + ") list_rows", Long.class, args.toArray());
        args.add(size); args.add(page * size);
        List<Map<String, Object>> items = jdbc.queryForList(data.sql() + where + " ORDER BY " + sortColumn + " " + dir + " LIMIT ? OFFSET ?", args.toArray());
        return new PageResult(items, page, size, total);
    }

    public List<Map<String, Object>> all(String resource, String query, String status, String role) {
        Dataset data = dataset(resource);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (resource.equals("accounts")) where.append(" AND u.deleted_at IS NULL AND u.role<>'ADMIN'");
        if (query != null && !query.isBlank()) { where.append(" AND ").append(data.search()).append(" LIKE ?"); args.add("%" + query.trim().toLowerCase(Locale.ROOT) + "%"); }
        if (status != null && !status.isBlank() && data.status() != null) { where.append(" AND ").append(data.status()).append("=?"); args.add(status.toUpperCase(Locale.ROOT)); }
        if (role != null && !role.isBlank() && resource.equals("accounts")) { where.append(" AND u.role=?"); args.add(role.toUpperCase(Locale.ROOT)); }
        return jdbc.queryForList(data.sql() + where + " ORDER BY " + data.sort().get(data.defaultSort()) + " DESC", args.toArray());
    }

    private Dataset dataset(String resource) {
        return switch (resource) {
            case "accounts" -> new Dataset("SELECT u.id userId,u.email,u.role,u.status,u.failed_login_attempts failedLoginAttempts,u.locked_until lockedUntil,COALESCE(ap.full_name,op.full_name,sp.full_name,cp.full_name) fullName,COALESCE(op.phone,sp.phone,cp.phone) phone,cp.customer_number customerNumber,cp.approval_status approvalStatus,u.created_at createdAt FROM users u LEFT JOIN admin_profile ap ON ap.user_id=u.id LEFT JOIN operator_profile op ON op.user_id=u.id LEFT JOIN support_profile sp ON sp.user_id=u.id LEFT JOIN customer_profile cp ON cp.user_id=u.id",
                    "LOWER(CONCAT_WS(' ',u.email,u.role,u.status,ap.full_name,op.full_name,sp.full_name,cp.full_name,cp.customer_number))", "u.status",
                    Map.of("id","u.id","email","u.email","role","u.role","status","u.status","name","COALESCE(ap.full_name,op.full_name,sp.full_name,cp.full_name)","createdAt","u.created_at"), "createdAt");
            case "bills" -> new Dataset("SELECT b.id,b.bill_number billNumber,b.cycle,b.issue_date issueDate,b.due_date dueDate,b.total_amount totalAmount,b.paid_amount paidAmount,b.status,cp.customer_number customerNumber,cp.full_name customerName,s.name service,c.meter_number meterNumber FROM bill b JOIN connection c ON c.id=b.connection_id JOIN customer_profile cp ON cp.user_id=c.customer_id JOIN utility_service s ON s.id=c.service_id",
                    "LOWER(CONCAT_WS(' ',b.bill_number,b.cycle,b.status,cp.customer_number,cp.full_name,s.name,c.meter_number))", "b.status",
                    Map.of("id","b.id","billNumber","b.bill_number","cycle","b.cycle","dueDate","b.due_date","totalAmount","b.total_amount","status","b.status"), "dueDate");
            case "payments" -> new Dataset("SELECT p.id,p.transaction_id transactionId,p.amount,p.method,p.status,p.paid_at paidAt,b.bill_number billNumber,cp.customer_number customerNumber,cp.full_name customerName FROM payment p JOIN bill b ON b.id=p.bill_id JOIN connection c ON c.id=b.connection_id JOIN customer_profile cp ON cp.user_id=c.customer_id",
                    "LOWER(CONCAT_WS(' ',p.transaction_id,p.method,p.status,b.bill_number,cp.customer_number,cp.full_name))", "p.status",
                    Map.of("id","p.id","transactionId","p.transaction_id","amount","p.amount","paidAt","p.paid_at","status","p.status"), "paidAt");
            case "readings" -> new Dataset("SELECT mr.id,mr.cycle,mr.previous_reading previousReading,mr.current_reading currentReading,mr.consumption,mr.reading_date readingDate,mr.rollover,mr.meter_reset meterReset,mr.remarks,c.meter_number meterNumber,cp.customer_number customerNumber,cp.full_name customerName,s.name service FROM meter_reading mr JOIN connection c ON c.id=mr.connection_id JOIN customer_profile cp ON cp.user_id=c.customer_id JOIN utility_service s ON s.id=c.service_id",
                    "LOWER(CONCAT_WS(' ',mr.cycle,c.meter_number,cp.customer_number,cp.full_name,s.name))", null,
                    Map.of("id","mr.id","cycle","mr.cycle","readingDate","mr.reading_date","meterNumber","c.meter_number"), "readingDate");
            case "tickets" -> new Dataset("SELECT t.id,t.ticket_number ticketNumber,t.category,t.subject,t.status,t.priority,t.due_at dueAt,t.created_at createdAt,cp.customer_number customerNumber,cp.full_name customerName,s.name service,COALESCE(sp.full_name,'Unassigned') assignedTo FROM ticket t JOIN customer_profile cp ON cp.user_id=t.customer_id JOIN utility_service s ON s.id=t.service_id LEFT JOIN support_profile sp ON sp.user_id=t.assigned_to",
                    "LOWER(CONCAT_WS(' ',t.ticket_number,t.category,t.subject,t.status,t.priority,cp.customer_number,cp.full_name,s.name))", "t.status",
                    Map.of("id","t.id","ticketNumber","t.ticket_number","priority","t.priority","status","t.status","createdAt","t.created_at","dueAt","t.due_at"), "createdAt");
            case "connectionRequests" -> new Dataset("SELECT r.id,r.request_type requestType,r.status,r.created_at createdAt,cp.customer_number customerNumber,cp.full_name customerName,s.name service FROM connection_request r JOIN customer_profile cp ON cp.user_id=r.customer_id JOIN utility_service s ON s.id=r.service_id",
                    "LOWER(CONCAT_WS(' ',r.request_type,r.status,cp.customer_number,cp.full_name,s.name))", "r.status",
                    Map.of("id","r.id","requestType","r.request_type","status","r.status","createdAt","r.created_at"), "createdAt");
            case "audit" -> new Dataset("SELECT a.id,a.action,a.entity_type entityType,a.entity_id entityId,a.ip_address ipAddress,a.old_value oldValue,a.new_value newValue,a.created_at createdAt,u.email actor FROM audit_log a LEFT JOIN users u ON u.id=a.actor_id",
                    "LOWER(CONCAT_WS(' ',a.action,a.entity_type,a.entity_id,a.ip_address,u.email))", null,
                    Map.of("id","a.id","action","a.action","entityType","a.entity_type","createdAt","a.created_at"), "createdAt");
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown data resource");
        };
    }

    @Transactional
    public Map<String, Object> createAccount(AccountInput input, String actor, String ip) {
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        AppUser user = users.save(new AppUser(email, passwords.encode(input.password()), input.role(), "ACTIVE"));
        insertProfile(user.getId(), input.role(), input.fullName().trim(), input.phone());
        audit(actor,"ACCOUNT_CREATED","users",user.getId(),ip,null,Map.of("email",email,"role",input.role().name(),"status","ACTIVE"));
        return account(user.getId());
    }

    @Transactional
    public Map<String, Object> updateAccount(long id, AccountUpdate input, String actor, String ip) {
        Map<String,Object> old=account(id);
        requireManagedAccount(old);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE LOWER(email)=LOWER(?) AND id<>? AND deleted_at IS NULL",Integer.class,input.email().trim(),id)>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Email is already registered");
        jdbc.update("UPDATE users SET email=? WHERE id=? AND deleted_at IS NULL",input.email().trim().toLowerCase(Locale.ROOT),id);
        PortalRole role=PortalRole.valueOf(old.get("role").toString());
        if (role == PortalRole.ADMIN) jdbc.update("UPDATE admin_profile SET full_name=? WHERE user_id=?",input.fullName().trim(),id);
        else jdbc.update("UPDATE "+profileTable(role)+" SET full_name=?,phone=? WHERE user_id=?",input.fullName().trim(),input.phone(),id);
        Map<String,Object> next=account(id);
        audit(actor,"ACCOUNT_UPDATED","users",id,ip,old,next);
        return next;
    }

    @Transactional
    public void setAccountStatus(long id, String status, String actor, String ip) {
        String next=status.toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE","INACTIVE","LOCKED").contains(next)) throw bad("Status must be ACTIVE, INACTIVE or LOCKED");
        Map<String,Object> old=account(id);
        requireManagedAccount(old);
        if ("CUSTOMER".equals(old.get("role")) && !"APPROVED".equals(old.get("approvalStatus")) && next.equals("ACTIVE")) throw bad("Approve the customer registration from the approvals queue");
        jdbc.update("UPDATE users SET status=?,locked_until=NULL,failed_login_attempts=0 WHERE id=?",next,id);
        audit(actor,"ACCOUNT_STATUS_CHANGED","users",id,ip,old,Map.of("status",next));
    }

    @Transactional
    public void deleteAccount(long id, String actor, String ip) {
        Map<String,Object> old=account(id);
        requireManagedAccount(old);
        jdbc.update("UPDATE users SET status='INACTIVE',deleted_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE id=?",id);
        jdbc.update("UPDATE refresh_token SET revoked_at=UTC_TIMESTAMP(6) WHERE user_id=? AND revoked_at IS NULL",id);
        audit(actor,"ACCOUNT_SOFT_DELETED","users",id,ip,old,Map.of("status","INACTIVE","deleted",true));
    }

    @Transactional
    public void resetPassword(long id, String newPassword, String actor, String ip) {
        Map<String,Object> old=account(id);
        requireManagedAccount(old);
        jdbc.update("UPDATE users SET password_hash=?,failed_login_attempts=0,locked_until=NULL WHERE id=?",passwords.encode(newPassword),id);
        jdbc.update("UPDATE refresh_token SET revoked_at=UTC_TIMESTAMP(6) WHERE user_id=? AND revoked_at IS NULL",id);
        audit(actor,"PASSWORD_RESET","users",id,ip,Map.of("password","[redacted]"),Map.of("password","[updated]"));
    }

    public PageResult registrations(String query,int page,int size) {
        return filteredPage("SELECT cp.user_id userId,u.email,cp.customer_number customerNumber,cp.full_name fullName,cp.phone,cp.approval_status approvalStatus,u.created_at createdAt FROM customer_profile cp JOIN users u ON u.id=cp.user_id",
                "cp.approval_status='PENDING_APPROVAL'", "LOWER(CONCAT_WS(' ',u.email,cp.customer_number,cp.full_name,cp.phone))",query,page,size,"createdAt",Map.of("createdAt","u.created_at","fullName","cp.full_name","email","u.email"));
    }

    public PageResult connectionRequests(String query,String status,int page,int size) {
        return page("connectionRequests",query,status,null,page,size,"createdAt","DESC");
    }

    @Transactional
    public void decideRegistration(long id,String decision,String actor,String ip) {
        String value=decision.toUpperCase(Locale.ROOT);
        if (!List.of("APPROVED","REJECTED").contains(value)) throw bad("Decision must be APPROVED or REJECTED");
        Map<String,Object> old=jdbc.queryForMap("SELECT cp.user_id userId,cp.approval_status approvalStatus,u.status FROM customer_profile cp JOIN users u ON u.id=cp.user_id WHERE cp.user_id=? AND cp.approval_status='PENDING_APPROVAL'",id);
        jdbc.update("UPDATE customer_profile SET approval_status=? WHERE user_id=?",value,id);
        jdbc.update("UPDATE users SET status=? WHERE id=?",value.equals("APPROVED")?"ACTIVE":"INACTIVE",id);
        audit(actor,"CUSTOMER_REGISTRATION_"+value,"customer_profile",id,ip,old,Map.of("approvalStatus",value));
        notifications.notify(id,"Registration "+value.toLowerCase(Locale.ROOT),value.equals("APPROVED")?"Your customer account is approved. You can now sign in to UtilityCare.":"Your customer account registration was not approved.","REGISTRATION_"+id+"_"+value,true);
    }

    @Transactional
    public void decideConnectionRequest(long id,String decision,String actor,String ip) {
        String value=decision.toUpperCase(Locale.ROOT);
        if (!List.of("APPROVED","REJECTED").contains(value)) throw bad("Decision must be APPROVED or REJECTED");
        Map<String,Object> old=jdbc.queryForMap("SELECT id,status,request_type requestType,customer_id customerId FROM connection_request WHERE id=? AND status='PENDING'",id);
        Long actorId=actorId(actor);
        jdbc.update("UPDATE connection_request SET status=?,request_status=?,reviewed_by=?,reviewed_at=UTC_TIMESTAMP(6) WHERE id=?",value,value,actorId,id);
        audit(actor,"CONNECTION_REQUEST_"+value,"connection_request",id,ip,old,Map.of("status",value));
        notifications.notify(((Number)old.get("customerId")).longValue(),"Connection request "+value.toLowerCase(Locale.ROOT),"Your "+old.get("requestType").toString().toLowerCase(Locale.ROOT).replace('_',' ')+" request has been "+value.toLowerCase(Locale.ROOT)+".","CONNECTION_REQUEST_"+id+"_"+value,true);
    }

    public List<Map<String,Object>> services() { return jdbc.queryForList("SELECT id,code,name,active FROM utility_service ORDER BY name"); }
    @Transactional public Map<String,Object> createService(String code,String name,String actor,String ip) {
        jdbc.update("INSERT INTO utility_service(code,name,active) VALUES (?,?,TRUE)",code.toUpperCase(Locale.ROOT),name.trim());
        Long id=jdbc.queryForObject("SELECT id FROM utility_service WHERE code=?",Long.class,code.toUpperCase(Locale.ROOT));
        Map<String,Object> value=Map.of("id",id,"code",code.toUpperCase(Locale.ROOT),"name",name.trim(),"active",true);
        audit(actor,"SERVICE_CREATED","utility_service",id,ip,null,value); return value;
    }
    @Transactional public Map<String,Object> updateService(long id,String code,String name,boolean active,String actor,String ip) {
        Map<String,Object> old=jdbc.queryForMap("SELECT id,code,name,active FROM utility_service WHERE id=?",id);
        jdbc.update("UPDATE utility_service SET code=?,name=?,active=? WHERE id=?",code.toUpperCase(Locale.ROOT),name.trim(),active,id);
        Map<String,Object> next=jdbc.queryForMap("SELECT id,code,name,active FROM utility_service WHERE id=?",id);
        audit(actor,"SERVICE_UPDATED","utility_service",id,ip,old,next); return next;
    }

    public List<Map<String,Object>> connectionTypes() { return jdbc.queryForList("SELECT id,name FROM connection_type ORDER BY name"); }
    @Transactional public Map<String,Object> createConnectionType(String name,String actor,String ip) {
        jdbc.update("INSERT INTO connection_type(name) VALUES (?)",name.trim().toUpperCase(Locale.ROOT));
        Long id=jdbc.queryForObject("SELECT id FROM connection_type WHERE name=?",Long.class,name.trim().toUpperCase(Locale.ROOT));
        Map<String,Object> next=Map.of("id",id,"name",name.trim().toUpperCase(Locale.ROOT));
        audit(actor,"CONNECTION_TYPE_CREATED","connection_type",id,ip,null,next);return next;
    }
    @Transactional public Map<String,Object> updateConnectionType(long id,String name,String actor,String ip) {
        Map<String,Object> old=jdbc.queryForMap("SELECT id,name FROM connection_type WHERE id=?",id);
        jdbc.update("UPDATE connection_type SET name=? WHERE id=?",name.trim().toUpperCase(Locale.ROOT),id);
        Map<String,Object> next=jdbc.queryForMap("SELECT id,name FROM connection_type WHERE id=?",id);
        audit(actor,"CONNECTION_TYPE_UPDATED","connection_type",id,ip,old,next);return next;
    }
    @Transactional public void deleteConnectionType(long id,String actor,String ip) {
        Map<String,Object> old=jdbc.queryForMap("SELECT id,name FROM connection_type WHERE id=?",id);
        jdbc.update("DELETE FROM connection_type WHERE id=?",id);
        audit(actor,"CONNECTION_TYPE_DELETED","connection_type",id,ip,old,null);
    }

    public List<Map<String,Object>> zones() { return jdbc.queryForList("SELECT id,name,description FROM zone ORDER BY name"); }
    @Transactional public Map<String,Object> createZone(String name,String description,String actor,String ip) {
        jdbc.update("INSERT INTO zone(name,description) VALUES (?,?)",name.trim(),description);
        Long id=jdbc.queryForObject("SELECT id FROM zone WHERE name=?",Long.class,name.trim());
        Map<String,Object> next=new LinkedHashMap<>();next.put("id",id);next.put("name",name.trim());next.put("description",description);
        audit(actor,"ZONE_CREATED","zone",id,ip,null,next);return next;
    }
    @Transactional public Map<String,Object> updateZone(long id,String name,String description,String actor,String ip) {
        Map<String,Object> old=jdbc.queryForMap("SELECT id,name,description FROM zone WHERE id=?",id);
        jdbc.update("UPDATE zone SET name=?,description=? WHERE id=?",name.trim(),description,id);
        Map<String,Object> next=jdbc.queryForMap("SELECT id,name,description FROM zone WHERE id=?",id);
        audit(actor,"ZONE_UPDATED","zone",id,ip,old,next);return next;
    }
    @Transactional public void deleteZone(long id,String actor,String ip) {
        Map<String,Object> old=jdbc.queryForMap("SELECT id,name,description FROM zone WHERE id=?",id);
        jdbc.update("DELETE FROM operator_zone WHERE zone_id=?",id);
        jdbc.update("UPDATE connection SET zone_id=NULL WHERE zone_id=?",id);
        jdbc.update("DELETE FROM zone WHERE id=?",id);
        audit(actor,"ZONE_DELETED","zone",id,ip,old,null);
    }

    public List<Map<String,Object>> tariffs() {
        return jdbc.queryForList("SELECT t.id,t.service_id serviceId,s.name service,t.connection_type_id connectionTypeId,ct.name connectionType,t.range_start rangeStart,t.range_end rangeEnd,t.rate_per_unit ratePerUnit,t.fixed_charge fixedCharge,t.tax_percent taxPercent,t.late_fee_percent lateFeePercent,t.due_days dueDays,t.effective_from effectiveFrom,t.effective_to effectiveTo FROM tariff_slab t JOIN utility_service s ON s.id=t.service_id JOIN connection_type ct ON ct.id=t.connection_type_id ORDER BY s.name,ct.name,t.range_start");
    }
    @Transactional public Map<String,Object> createTariff(TariffInput t,String actor,String ip) {
        validateTariff(t);
        jdbc.update("INSERT INTO tariff_slab(service_id,connection_type_id,range_start,range_end,rate_per_unit,fixed_charge,tax_percent,late_fee_percent,due_days,effective_from,effective_to) VALUES (?,?,?,?,?,?,?,?,?,?,?)",t.serviceId(),t.connectionTypeId(),t.rangeStart(),t.rangeEnd(),t.ratePerUnit(),t.fixedCharge(),t.taxPercent(),t.lateFeePercent(),t.dueDays(),t.effectiveFrom(),t.effectiveTo());
        Long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        Map<String,Object> next=tariff(id);audit(actor,"TARIFF_CREATED","tariff_slab",id,ip,null,next);return next;
    }
    @Transactional public Map<String,Object> updateTariff(long id,TariffInput t,String actor,String ip) {
        validateTariff(t);Map<String,Object> old=tariff(id);
        jdbc.update("UPDATE tariff_slab SET service_id=?,connection_type_id=?,range_start=?,range_end=?,rate_per_unit=?,fixed_charge=?,tax_percent=?,late_fee_percent=?,due_days=?,effective_from=?,effective_to=? WHERE id=?",t.serviceId(),t.connectionTypeId(),t.rangeStart(),t.rangeEnd(),t.ratePerUnit(),t.fixedCharge(),t.taxPercent(),t.lateFeePercent(),t.dueDays(),t.effectiveFrom(),t.effectiveTo(),id);
        Map<String,Object> next=tariff(id);audit(actor,"TARIFF_UPDATED","tariff_slab",id,ip,old,next);return next;
    }
    @Transactional public void deleteTariff(long id,String actor,String ip) {
        Map<String,Object> old=tariff(id);jdbc.update("DELETE FROM tariff_slab WHERE id=?",id);audit(actor,"TARIFF_DELETED","tariff_slab",id,ip,old,null);
    }

    public Map<String,Object> assignments() {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("operators",jdbc.queryForList("SELECT u.id userId,u.email,op.full_name fullName FROM users u JOIN operator_profile op ON op.user_id=u.id WHERE u.deleted_at IS NULL ORDER BY op.full_name"));
        result.put("customers",jdbc.queryForList("SELECT u.id userId,u.email,cp.customer_number customerNumber,cp.full_name fullName FROM users u JOIN customer_profile cp ON cp.user_id=u.id WHERE u.deleted_at IS NULL AND cp.approval_status='APPROVED' ORDER BY cp.full_name"));
        result.put("zones",zones());
        result.put("zoneAssignments",jdbc.queryForList("SELECT operator_id operatorId,zone_id zoneId FROM operator_zone"));
        result.put("customerAssignments",jdbc.queryForList("SELECT operator_id operatorId,customer_id customerId FROM operator_customer"));
        return result;
    }
    @Transactional public void assignZones(long operatorId,List<Long> zoneIds,String actor,String ip) {
        validateIds(zoneIds,"zone");
        assertOperator(operatorId);Map<String,Object> old=jdbc.queryForMap("SELECT COUNT(*) total FROM operator_zone WHERE operator_id=?",operatorId);
        if(!zoneIds.isEmpty()&&jdbc.queryForObject("SELECT COUNT(*) FROM zone WHERE id IN ("+placeholders(zoneIds.size())+")",Integer.class,zoneIds.toArray())!=zoneIds.size())throw bad("One or more zones do not exist");
        jdbc.update("DELETE FROM operator_zone WHERE operator_id=?",operatorId);
        for(Long zoneId:zoneIds)jdbc.update("INSERT INTO operator_zone(operator_id,zone_id) VALUES (?,?)",operatorId,zoneId);
        audit(actor,"OPERATOR_ZONES_ASSIGNED","operator_zone",operatorId,ip,old,Map.of("zoneIds",zoneIds));
    }
    @Transactional public void assignCustomers(long operatorId,List<Long> customerIds,String actor,String ip) {
        validateIds(customerIds,"customer");
        assertOperator(operatorId);Map<String,Object> old=jdbc.queryForMap("SELECT COUNT(*) total FROM operator_customer WHERE operator_id=?",operatorId);
        if(!customerIds.isEmpty()&&jdbc.queryForObject("SELECT COUNT(*) FROM customer_profile WHERE approval_status='APPROVED' AND user_id IN ("+placeholders(customerIds.size())+")",Integer.class,customerIds.toArray())!=customerIds.size())throw bad("One or more customers are not approved or do not exist");
        jdbc.update("DELETE FROM operator_customer WHERE operator_id=?",operatorId);
        for(Long customerId:customerIds)jdbc.update("INSERT INTO operator_customer(operator_id,customer_id) VALUES (?,?)",operatorId,customerId);
        audit(actor,"OPERATOR_CUSTOMERS_ASSIGNED","operator_customer",operatorId,ip,old,Map.of("customerIds",customerIds));
    }

    public Map<String,Object> reports() {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("monthlyRevenue",jdbc.queryForList("SELECT DATE_FORMAT(paid_at,'%Y-%m') month,SUM(amount) revenue FROM payment WHERE status='SUCCESS' GROUP BY DATE_FORMAT(paid_at,'%Y-%m') ORDER BY month DESC LIMIT 24"));
        result.put("serviceConsumption",jdbc.queryForList("SELECT s.name service,COALESCE(SUM(m.consumption),0) consumption FROM utility_service s LEFT JOIN connection c ON c.service_id=s.id LEFT JOIN meter_reading m ON m.connection_id=c.id GROUP BY s.id,s.name ORDER BY s.name"));
        result.put("defaulters",jdbc.queryForList("SELECT cp.customer_number customerNumber,cp.full_name customerName,u.email,b.bill_number billNumber,b.due_date dueDate,b.total_amount-b.paid_amount amountDue,s.name service FROM bill b JOIN connection c ON c.id=b.connection_id JOIN customer_profile cp ON cp.user_id=c.customer_id JOIN users u ON u.id=cp.user_id JOIN utility_service s ON s.id=c.service_id WHERE b.due_date<UTC_DATE() AND b.total_amount>b.paid_amount AND b.status<>'CANCELLED' ORDER BY amountDue DESC"));
        result.put("operatorPerformance",jdbc.queryForList("SELECT u.id operatorId,op.full_name operatorName,(SELECT COUNT(DISTINCT oc.customer_id) FROM operator_customer oc WHERE oc.operator_id=u.id)+(SELECT COUNT(DISTINCT c.customer_id) FROM operator_zone oz JOIN connection c ON c.zone_id=oz.zone_id WHERE oz.operator_id=u.id)-(SELECT COUNT(DISTINCT oc2.customer_id) FROM operator_customer oc2 JOIN connection c2 ON c2.customer_id=oc2.customer_id JOIN operator_zone oz2 ON oz2.zone_id=c2.zone_id AND oz2.operator_id=oc2.operator_id WHERE oc2.operator_id=u.id) assignedCustomers,(SELECT COUNT(*) FROM meter_reading mr WHERE mr.recorded_by=u.id) readingsRecorded FROM users u JOIN operator_profile op ON op.user_id=u.id WHERE u.deleted_at IS NULL ORDER BY readingsRecorded DESC"));
        result.put("ticketResolution",jdbc.queryForList("SELECT s.name service,COUNT(t.id) resolvedTickets,ROUND(AVG(TIMESTAMPDIFF(MINUTE,t.created_at,t.resolved_at))/60,2) averageHours FROM utility_service s LEFT JOIN ticket t ON t.service_id=s.id AND t.resolved_at IS NOT NULL GROUP BY s.id,s.name"));
        return result;
    }

    public ExportFile export(String resource,String query,String status,String role,String format) {
        List<Map<String,Object>> rows=all(resource,query,status,role);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No rows match this export");
        try {
            if("csv".equalsIgnoreCase(format)){
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                try(CSVPrinter printer=new CSVPrinter(new OutputStreamWriter(out,StandardCharsets.UTF_8),CSVFormat.DEFAULT.builder().setHeader(rows.get(0).keySet().toArray(String[]::new)).build())){
                    for(Map<String,Object> row:rows)printer.printRecord(row.values());
                }
                return new ExportFile(out.toByteArray(),"text/csv; charset=utf-8",resource+".csv");
            }
            if("pdf".equalsIgnoreCase(format)){
                ByteArrayOutputStream out=new ByteArrayOutputStream();Document doc=new Document();PdfWriter.getInstance(doc,out);doc.open();
                doc.add(new Paragraph("UtilityCare — "+resource+" export",new Font(Font.HELVETICA,16,Font.BOLD)));
                doc.add(new Paragraph("Created at "+Instant.now().toString(),new Font(Font.HELVETICA,9,Font.NORMAL)));doc.add(new Paragraph(" "));
                String[] headers=rows.get(0).keySet().toArray(String[]::new);PdfPTable table=new PdfPTable(headers.length);table.setWidthPercentage(100);
                for(String h:headers){PdfPCell cell=new PdfPCell(new Phrase(h,new Font(Font.HELVETICA,8,Font.BOLD)));cell.setHorizontalAlignment(Element.ALIGN_LEFT);table.addCell(cell);}
                for(Map<String,Object> row:rows)for(Object value:row.values())table.addCell(new Phrase(value==null?"":String.valueOf(value),new Font(Font.HELVETICA,7,Font.NORMAL)));
                doc.add(table);doc.close();return new ExportFile(out.toByteArray(),"application/pdf",resource+".pdf");
            }
            throw bad("Format must be csv or pdf");
        } catch(Exception ex){throw new IllegalStateException("Could not create export",ex);}
    }

    private PageResult filteredPage(String sql,String condition,String search,String query,int page,int size,String defaultSort,Map<String,String> sort) {
        if(page<0||size<1||size>100)throw bad("Page must be non-negative and size must be between 1 and 100");
        String where=" WHERE "+condition;List<Object> args=new ArrayList<>();
        if(query!=null&&!query.isBlank()){where+=" AND "+search+" LIKE ?";args.add("%"+query.trim().toLowerCase(Locale.ROOT)+"%");}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM ("+sql+where+") rows_data",Long.class,args.toArray());
        String sortColumn=sort.get(defaultSort);args.add(size);args.add(page*size);
        List<Map<String,Object>> items=jdbc.queryForList(sql+where+" ORDER BY "+sortColumn+" DESC LIMIT ? OFFSET ?",args.toArray());
        return new PageResult(items,page,size,total);
    }
    private Object scalar(String sql){return jdbc.queryForObject(sql,Object.class);}
    private BigDecimal money(String sql){BigDecimal value=jdbc.queryForObject(sql,BigDecimal.class);return value==null?BigDecimal.ZERO:value;}
    private Map<String,Object> account(long id){try{return jdbc.queryForMap("SELECT u.id userId,u.email,u.role,u.status,COALESCE(ap.full_name,op.full_name,sp.full_name,cp.full_name) fullName,COALESCE(op.phone,sp.phone,cp.phone) phone,cp.approval_status approvalStatus FROM users u LEFT JOIN admin_profile ap ON ap.user_id=u.id LEFT JOIN operator_profile op ON op.user_id=u.id LEFT JOIN support_profile sp ON sp.user_id=u.id LEFT JOIN customer_profile cp ON cp.user_id=u.id WHERE u.id=? AND u.deleted_at IS NULL",id);}catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Account not found");}}
    private void requireManagedAccount(Map<String,Object> account){
        if(account.get("role")==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Account role not found");
    }
    private void insertProfile(long id,PortalRole role,String name,String phone){
        switch(role){case ADMIN->jdbc.update("INSERT INTO admin_profile(user_id,full_name) VALUES (?,?)",id,name);case OPERATOR->jdbc.update("INSERT INTO operator_profile(user_id,full_name,phone) VALUES (?,?,?)",id,name,phone);case HELP_SUPPORT->jdbc.update("INSERT INTO support_profile(user_id,full_name,phone) VALUES (?,?,?)",id,name,phone);case CUSTOMER->jdbc.update("INSERT INTO customer_profile(user_id,customer_number,full_name,phone,approval_status) VALUES (?,?,?,?,?)",id,"CUS-"+id,name,phone,"APPROVED");}
    }
    private String profileTable(PortalRole role){return switch(role){case OPERATOR->"operator_profile";case HELP_SUPPORT->"support_profile";case CUSTOMER->"customer_profile";case ADMIN->"admin_profile";};}
    private void validateTariff(TariffInput t){
        if(t.rangeStart().signum()<0||t.ratePerUnit().signum()<0||t.fixedCharge().signum()<0||t.taxPercent().signum()<0||t.taxPercent().compareTo(BigDecimal.valueOf(100))>0||t.lateFeePercent().signum()<0||t.lateFeePercent().compareTo(BigDecimal.valueOf(100))>0||t.dueDays()<1||t.dueDays()>90||(t.rangeEnd()!=null&&t.rangeEnd().compareTo(t.rangeStart())<=0))throw bad("Tariff values are outside their allowed ranges");
    }
    private Map<String,Object> tariff(long id){try{return jdbc.queryForMap("SELECT t.id,t.service_id serviceId,s.name service,t.connection_type_id connectionTypeId,ct.name connectionType,t.range_start rangeStart,t.range_end rangeEnd,t.rate_per_unit ratePerUnit,t.fixed_charge fixedCharge,t.tax_percent taxPercent,t.late_fee_percent lateFeePercent,t.due_days dueDays,t.effective_from effectiveFrom,t.effective_to effectiveTo FROM tariff_slab t JOIN utility_service s ON s.id=t.service_id JOIN connection_type ct ON ct.id=t.connection_type_id WHERE t.id=?",id);}catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Tariff not found");}}
    private void assertOperator(long id){if(jdbc.queryForObject("SELECT COUNT(*) FROM operator_profile WHERE user_id=?",Integer.class,id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Operator not found");}
    private void validateIds(List<Long> ids,String kind){if(ids.size()>1000||ids.stream().distinct().count()!=ids.size())throw bad("Provide up to 1000 unique "+kind+" IDs");}
    private String placeholders(int count){return String.join(",",java.util.Collections.nCopies(count,"?"));}
    private Long actorId(String email){return jdbc.queryForObject("SELECT id FROM users WHERE email=? AND deleted_at IS NULL",Long.class,email);}
    private void audit(String actor,String action,String entity,Object entityId,String ip,Object oldValue,Object newValue){
        jdbc.update("INSERT INTO audit_log(actor_id,action,entity_type,entity_id,ip_address,old_value,new_value) VALUES (?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON))",
                actorId(actor),action,entity,String.valueOf(entityId),ip,serialize(oldValue),serialize(newValue));
    }
    private String serialize(Object value){if(value==null)return null;try{return json.writeValueAsString(value);}catch(JsonProcessingException ex){throw new IllegalStateException("Could not record audit data",ex);}}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}

    public record PageResult(List<Map<String,Object>> items,int page,int size,long total){}
    public record AccountInput(String email,String password,PortalRole role,String fullName,String phone){}
    public record AccountUpdate(String email,String fullName,String phone){}
    public record TariffInput(long serviceId,long connectionTypeId,BigDecimal rangeStart,BigDecimal rangeEnd,BigDecimal ratePerUnit,BigDecimal fixedCharge,BigDecimal taxPercent,BigDecimal lateFeePercent,int dueDays,java.time.LocalDate effectiveFrom,java.time.LocalDate effectiveTo){}
    public record ExportFile(byte[] bytes,String contentType,String filename){}
    private record Dataset(String sql,String search,String status,Map<String,String> sort,String defaultSort){}
}

