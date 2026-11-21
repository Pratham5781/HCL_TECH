package com.utility.billing.auth;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="users")
public class AppUser {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true,length=190) private String email;
 @Column(name="password_hash",nullable=false,length=100) private String passwordHash;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private PortalRole role;
 @Column(nullable=false,length=24) private String status;
 @Column(name="failed_login_attempts",nullable=false) private int failedLoginAttempts;
 @Column(name="locked_until") private Instant lockedUntil;
 @Column(name="last_login_at") private Instant lastLoginAt;
 @Column(name="deleted_at") private Instant deletedAt;
 protected AppUser(){}
 public AppUser(String email,String passwordHash,PortalRole role,String status){this.email=email;this.passwordHash=passwordHash;this.role=role;this.status=status;}
 public Long getId(){return id;} public String getEmail(){return email;} public String getPasswordHash(){return passwordHash;} public PortalRole getRole(){return role;} public String getStatus(){return status;} public int getFailedLoginAttempts(){return failedLoginAttempts;} public Instant getLockedUntil(){return lockedUntil;}
 public void failedLogin(){failedLoginAttempts++;if(failedLoginAttempts>=5)lockedUntil=Instant.now().plusSeconds(1800);}
 public void markLogin(){lastLoginAt=Instant.now();failedLoginAttempts=0;lockedUntil=null;}
 public void updatePassword(String passwordHash){this.passwordHash=passwordHash;this.failedLoginAttempts=0;this.lockedUntil=null;}
}

