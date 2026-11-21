package com.utility.billing.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;private final JavaMailSender mail;private final String from;
    public NotificationService(JdbcTemplate jdbc,JavaMailSender mail,@Value("${app.mail-from:noreply@utility.local}")String from){this.jdbc=jdbc;this.mail=mail;this.from=from;}

    @Transactional
    public long notify(long userId,String title,String body,String eventKey,boolean sendEmail){
        jdbc.update("INSERT INTO notification(user_id,title,body,event_key,email_status) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)",userId,title,body,eventKey,sendEmail?"PENDING":"NOT_REQUESTED");
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        return id;
    }

    @Transactional
    public boolean deliver(long notificationId){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT n.user_id userId,n.title,n.body,n.email_status emailStatus,u.email FROM notification n JOIN users u ON u.id=n.user_id WHERE n.id=?",notificationId);
        if(rows.isEmpty())return false;Map<String,Object> row=rows.get(0);if("SENT".equals(row.get("emailStatus"))||"NOT_REQUESTED".equals(row.get("emailStatus")))return false;
        try{SimpleMailMessage message=new SimpleMailMessage();message.setFrom(from);message.setTo(row.get("email").toString());message.setSubject(row.get("title").toString());message.setText(row.get("body").toString());mail.send(message);jdbc.update("UPDATE notification SET email_status='SENT',email_sent_at=UTC_TIMESTAMP(6),email_error=NULL WHERE id=?",notificationId);return true;}
        catch(Exception ex){String reason=ex.getMessage()==null?"Email delivery failed":ex.getMessage();jdbc.update("UPDATE notification SET email_status='FAILED',email_error=?,email_attempts=email_attempts+1,email_next_attempt_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL CASE WHEN email_attempts<1 THEN 2 WHEN email_attempts<2 THEN 5 WHEN email_attempts<4 THEN 15 WHEN email_attempts<6 THEN 60 ELSE 1440 END MINUTE) WHERE id=?",reason.substring(0,Math.min(reason.length(),500)),notificationId);return false;}
    }

    public PageResult list(String email,boolean unreadOnly,int page,int size){long user=userId(email);validatePage(page,size);String where=" WHERE user_id=?"+(unreadOnly?" AND read_at IS NULL":"");long total=jdbc.queryForObject("SELECT COUNT(*) FROM notification"+where,Long.class,user);List<Object> args=new ArrayList<>();args.add(user);args.add(size);args.add(page*size);return new PageResult(jdbc.queryForList("SELECT id,title,body,event_key eventKey,read_at readAt,email_status emailStatus,created_at createdAt FROM notification"+where+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",args.toArray()),page,size,total,jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id=? AND read_at IS NULL",Long.class,user));}

    @Transactional public void markRead(String email,long id){long user=userId(email);int updated=jdbc.update("UPDATE notification SET read_at=COALESCE(read_at,UTC_TIMESTAMP(6)) WHERE id=? AND user_id=?",id,user);if(updated==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Notification not found");}
    @Transactional public int markAllRead(String email){return jdbc.update("UPDATE notification SET read_at=UTC_TIMESTAMP(6) WHERE user_id=? AND read_at IS NULL",userId(email));}
    private long userId(String email){try{return jdbc.queryForObject("SELECT id FROM users WHERE email=? AND status='ACTIVE' AND deleted_at IS NULL",Long.class,email);}catch(EmptyResultDataAccessException e){throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Account is not active");}}
    private void validatePage(int page,int size){if(page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Page must be non-negative and size must be between 1 and 100");}
    public record PageResult(List<Map<String,Object>> items,int page,int size,long total,long unreadCount){}
}
