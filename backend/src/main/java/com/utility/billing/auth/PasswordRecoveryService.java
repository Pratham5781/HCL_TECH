package com.utility.billing.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordRecoveryService {
 private final UserRepository users; private final PasswordResetTokenRepository tokens; private final JavaMailSender mail; private final String frontendUrl,from; private final Set<String> frontendOrigins;
 private final SecureRandom random=new SecureRandom();
 public PasswordRecoveryService(UserRepository users,PasswordResetTokenRepository tokens,JavaMailSender mail,@Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,@Value("${app.mail-from:noreply@utility.local}") String from,@Value("${app.cors-origins:http://localhost:5173}") String corsOrigins){this.users=users;this.tokens=tokens;this.mail=mail;this.frontendUrl=frontendUrl;this.from=from;this.frontendOrigins=Arrays.stream(corsOrigins.split(",")).map(String::trim).collect(Collectors.toUnmodifiableSet());}
 @Transactional public void request(String email,String origin){
  users.findByEmailIgnoreCaseAndStatusAndDeletedAtIsNull(email,"ACTIVE").ifPresent(user->{
   String raw=generate();tokens.save(new PasswordResetToken(user,hash(raw),Instant.now().plusSeconds(1800)));
   String resetBaseUrl=origin!=null&&frontendOrigins.contains(origin)?origin:frontendUrl;
   SimpleMailMessage message=new SimpleMailMessage();message.setFrom(from);message.setTo(user.getEmail());message.setSubject("Reset your UtilityCare password");message.setText("Use this link within 30 minutes to reset your password: "+resetBaseUrl+"/reset-password?token="+raw);
   mail.send(message);
  });
 }
 @Transactional public void reset(String raw,String newPassword,org.springframework.security.crypto.password.PasswordEncoder encoder){
  PasswordResetToken token=tokens.findByTokenHash(hash(raw)).orElseThrow(PasswordRecoveryService::invalid);
  if(token.getUsedAt()!=null||token.getExpiresAt().isBefore(Instant.now()))throw invalid();
  AppUser user=token.getUser();
  user.updatePassword(encoder.encode(newPassword));users.save(user);token.markUsed();tokens.save(token);
 }
 private static org.springframework.web.server.ResponseStatusException invalid(){return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Password reset token is invalid or expired");}
 private String generate(){byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 private String hash(String token){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
}

