package com.utility.billing.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
 private final javax.crypto.SecretKey key;
 private final long accessMinutes;
 public JwtService(@Value("${security.jwt.secret}") String secret,@Value("${security.jwt.access-minutes:15}") long accessMinutes){
  if(secret.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
  this.key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));this.accessMinutes=accessMinutes;
 }
 public String createAccessToken(AppUser user){
  Instant now=Instant.now();
  return Jwts.builder().subject(user.getEmail()).claim("userId",user.getId()).claim("role",user.getRole().name()).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(accessMinutes*60))).signWith(key).compact();
 }
 public String subject(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();}
}

