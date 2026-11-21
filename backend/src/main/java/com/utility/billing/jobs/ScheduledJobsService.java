package com.utility.billing.jobs;

import com.utility.billing.notification.NotificationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ScheduledJobsService {
    private final JdbcTemplate jdbc;private final NotificationService notifications;private final BigDecimal reminderDays;
    public ScheduledJobsService(JdbcTemplate jdbc,NotificationService notifications,@Value("${app.jobs.reminder-days:3}")BigDecimal reminderDays){this.jdbc=jdbc;this.notifications=notifications;this.reminderDays=reminderDays;}

    @Scheduled(cron="${app.jobs.overdue-cron:0 10 0 * * *}",zone="UTC")
    @Transactional
    public void markOverdueAndApplyLateFees(){
        List<Map<String,Object>> due=jdbc.queryForList("SELECT b.id,b.total_amount totalAmount,b.paid_amount paidAmount,b.due_date dueDate,b.status,c.service_id serviceId,c.connection_type_id connectionTypeId,b.bill_number billNumber,c.customer_id customerId FROM bill b JOIN connection c ON c.id=b.connection_id WHERE b.due_date<UTC_DATE() AND b.status NOT IN ('PAID','CANCELLED') AND b.total_amount>b.paid_amount ORDER BY b.id FOR UPDATE");
        LocalDate feeDate=LocalDate.now(java.time.ZoneOffset.UTC);
        for(Map<String,Object> bill:due){
            long id=((Number)bill.get("id")).longValue();
            BigDecimal percent=jdbc.queryForObject("SELECT COALESCE((SELECT late_fee_percent FROM tariff_slab WHERE service_id=? AND connection_type_id=? AND effective_from<=? AND (effective_to IS NULL OR effective_to>=?) ORDER BY effective_from DESC,range_start LIMIT 1),0)",BigDecimal.class,bill.get("serviceId"),bill.get("connectionTypeId"),bill.get("dueDate"),bill.get("dueDate"));
            BigDecimal balance=((BigDecimal)bill.get("totalAmount")).subtract((BigDecimal)bill.get("paidAmount")).max(BigDecimal.ZERO);
            BigDecimal fee=balance.multiply(percent).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
            int inserted=jdbc.update("INSERT IGNORE INTO bill_late_fee_entry(bill_id,fee_date,amount) VALUES (?,?,?)",id,feeDate,fee);
            if(inserted==1){jdbc.update("UPDATE bill SET late_fee=late_fee+?,total_amount=total_amount+?,status='OVERDUE',version=version+1 WHERE id=? AND total_amount>paid_amount AND status NOT IN ('PAID','CANCELLED')",fee,fee,id);jdbc.update("INSERT INTO audit_log(actor_id,action,entity_type,entity_id,old_value,new_value) VALUES (NULL,'DAILY_LATE_FEE_APPLIED','bill',?,JSON_OBJECT('status',?),JSON_OBJECT('status','OVERDUE','lateFeeAdded',?,'feeDate',?))",String.valueOf(id),bill.get("status"),fee,feeDate.toString());}
            else jdbc.update("UPDATE bill SET status='OVERDUE' WHERE id=? AND status NOT IN ('PAID','CANCELLED')",id);
        }
    }

    @Scheduled(cron="${app.jobs.reminder-cron:0 15 0 * * *}",zone="UTC")
    @Transactional
    public void sendDueReminders(){
        List<Map<String,Object>> due=jdbc.queryForList("SELECT b.id billId,b.bill_number billNumber,b.due_date dueDate,(b.total_amount-b.paid_amount) balance,c.customer_id customerId,cp.full_name customerName,cp.customer_number customerNumber,s.name service FROM bill b JOIN connection c ON c.id=b.connection_id JOIN customer_profile cp ON cp.user_id=c.customer_id JOIN utility_service s ON s.id=c.service_id WHERE b.due_date=DATE_ADD(UTC_DATE(),INTERVAL ? DAY) AND b.total_amount>b.paid_amount AND b.status IN ('GENERATED','PARTIALLY_PAID')",reminderDays.intValue());
        for(Map<String,Object> bill:due){String dueDate=bill.get("dueDate").toString();String key="BILL_DUE_"+bill.get("billId")+"_"+dueDate;String body="Hello "+bill.get("customerName")+", bill "+bill.get("billNumber")+" for "+bill.get("service")+" is due on "+dueDate+". Outstanding amount: Rs. "+bill.get("balance")+".";notifications.notify(((Number)bill.get("customerId")).longValue(),"Utility bill due soon",body,key,true);}
    }

    @Scheduled(cron="${app.jobs.token-purge-cron:0 30 2 * * *}",zone="UTC")
    @Transactional
    public void purgeExpiredTokens(){jdbc.update("DELETE FROM refresh_token WHERE expires_at<UTC_TIMESTAMP(6) OR revoked_at<DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 30 DAY)");jdbc.update("DELETE FROM password_reset_token WHERE expires_at<UTC_TIMESTAMP(6) OR used_at<DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 30 DAY)");}

    @Scheduled(cron="${app.jobs.email-retry-cron:0 */10 * * * *}",zone="UTC")
    public void retryFailedEmails(){List<Long> ids=jdbc.queryForList("SELECT id FROM notification WHERE email_status IN ('PENDING','FAILED') AND (email_next_attempt_at IS NULL OR email_next_attempt_at<=UTC_TIMESTAMP(6)) ORDER BY created_at LIMIT 100",Long.class);for(Long id:ids)notifications.deliver(id);}
}
