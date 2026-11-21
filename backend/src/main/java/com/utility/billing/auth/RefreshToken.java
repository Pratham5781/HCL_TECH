package com.utility.billing.auth;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="refresh_token")
public class RefreshToken {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private AppUser user;
 @Column(name="token_hash",nullable=false,unique=true,length=64,columnDefinition="CHAR(64)") private String tokenHash;
 @Column(name="expires_at",nullable=false) private Instant expiresAt;
 @Column(name="revoked_at") private Instant revokedAt;
 protected RefreshToken(){}
 public RefreshToken(AppUser user,String tokenHash,Instant expiresAt){this.user=user;this.tokenHash=tokenHash;this.expiresAt=expiresAt;}
 public Long getId(){return id;} public AppUser getUser(){return user;} public Instant getExpiresAt(){return expiresAt;} public Instant getRevokedAt(){return revokedAt;}
 public void revoke(){revokedAt=Instant.now();}
}

