package com.utility.billing.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
 private final UserRepository users; private final RefreshTokenRepository refreshTokens; private final PasswordEncoder encoder; private final JwtService jwt; private final JdbcTemplate jdbc; private final long refreshDays;
 private final SecureRandom random=new SecureRandom();
 public AuthService(UserRepository users,RefreshTokenRepository refreshTokens,PasswordEncoder encoder,JwtService jwt,JdbcTemplate jdbc,@Value("${security.jwt.refresh-days:7}") long refreshDays){this.users=users;this.refreshTokens=refreshTokens;this.encoder=encoder;this.jwt=jwt;this.jdbc=jdbc;this.refreshDays=refreshDays;}
 @Transactional(noRollbackFor=ResponseStatusException.class) public TokenPair login(String email,String password,PortalRole portal){
  var user=users.findByEmailIgnoreCaseAndStatusAndDeletedAtIsNull(email,"ACTIVE").orElseThrow(()->{
   if(portal==PortalRole.CUSTOMER&&users.findByEmailIgnoreCaseAndStatusAndDeletedAtIsNull(email,"PENDING_APPROVAL").filter(pending->pending.getRole()==PortalRole.CUSTOMER).isPresent())
    return new ResponseStatusException(HttpStatus.FORBIDDEN,"Customer registration is pending Admin approval");
   return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
  });
  if(user.getRole()!=portal)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Invalid portal for this account");
  if(user.getLockedUntil()!=null&&user.getLockedUntil().isAfter(Instant.now()))throw new ResponseStatusException(HttpStatus.LOCKED,"Account locked for 30 minutes after five failed attempts");
  if(!encoder.matches(password,user.getPasswordHash())){user.failedLogin();users.save(user);throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");}
  user.markLogin();users.save(user);return issue(user);
 }
 @Transactional public TokenPair refresh(String raw){
  var token=refreshTokens.findByTokenHash(hash(raw)).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid refresh token"));
  if(token.getRevokedAt()!=null||token.getExpiresAt().isBefore(Instant.now()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Expired refresh token");
  token.revoke();refreshTokens.save(token);return issue(token.getUser());
 }
 @Transactional public void logout(String raw){refreshTokens.findByTokenHash(hash(raw)).ifPresent(t->{t.revoke();refreshTokens.save(t);});}
 private TokenPair issue(AppUser user){String raw=randomToken();refreshTokens.save(new RefreshToken(user,hash(raw),Instant.now().plusSeconds(refreshDays*86400)));return new TokenPair(jwt.createAccessToken(user),raw,user.getRole().name(),user.getEmail());}
 @Transactional public void register(String email,String password,String name,String phone){
  if(users.existsByEmailIgnoreCaseAndDeletedAtIsNull(email))throw new ResponseStatusException(HttpStatus.CONFLICT,"Email is already registered");
  AppUser user=users.save(new AppUser(email.toLowerCase(),encoder.encode(password),PortalRole.CUSTOMER,"PENDING_APPROVAL"));
  jdbc.update("INSERT INTO customer_profile(user_id,customer_number,full_name,phone,approval_status) VALUES(?,?,?,?,?)",user.getId(),"CUS-"+user.getId(),name,phone,"PENDING_APPROVAL");
 }
 private String randomToken(){byte[] bytes=new byte[48];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 private String hash(String token){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 public record TokenPair(String accessToken,String refreshToken,String role,String email){}
}

