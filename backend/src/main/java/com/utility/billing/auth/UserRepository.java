package com.utility.billing.auth;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserRepository extends JpaRepository<AppUser,Long> {
 Optional<AppUser> findByEmailIgnoreCaseAndStatusAndDeletedAtIsNull(String email,String status);
    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);
}

