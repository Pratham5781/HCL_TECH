package com.utility.billing.config;

import com.utility.billing.auth.AppUser;
import com.utility.billing.auth.PortalRole;
import com.utility.billing.auth.UserRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SeedData {
    @Bean
    CommandLineRunner seed(UserRepository users, PasswordEncoder encoder, JdbcTemplate jdbc,
                           @Value("${app.seed-enabled:true}") boolean enabled) {
        return args -> {
            if (!enabled || users.count() > 0) return;
            long admin = add(users, encoder, jdbc, "admin@utility.com", PortalRole.ADMIN, "Alex Admin");
            long operator1 = add(users, encoder, jdbc, "operator1@utility.com", PortalRole.OPERATOR, "Olivia Operator");
            long operator2 = add(users, encoder, jdbc, "operator2@utility.com", PortalRole.OPERATOR, "Omar Operator");
            add(users, encoder, jdbc, "support1@utility.com", PortalRole.HELP_SUPPORT, "Sam Support");
            add(users, encoder, jdbc, "support2@utility.com", PortalRole.HELP_SUPPORT, "Sasha Support");
            jdbc.update("INSERT INTO operator_zone(operator_id,zone_id) VALUES (?,1),(?,2),(?,3)", operator1, operator1, operator2);
            for (int i = 1; i <= 5; i++) {
                long customer = add(users, encoder, jdbc, "customer" + i + "@utility.com", PortalRole.CUSTOMER, "Customer " + i);
                seedCustomerSample(jdbc, customer, i);
            }
            jdbc.update("INSERT INTO ticket(ticket_number,customer_id,service_id,category,subject,description,status,priority,assigned_to,due_at) VALUES ('DEMO-TKT-1001',(SELECT user_id FROM customer_profile WHERE customer_number='CUS-6'),1,'BILLING_DISPUTE','Question about electricity bill','Please review the consumption shown on the latest electricity bill.','OPEN','NORMAL',?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 24 HOUR))", admin);
            jdbc.update("INSERT INTO ticket(ticket_number,customer_id,service_id,category,subject,description,status,priority,assigned_to,due_at) VALUES ('DEMO-TKT-1002',(SELECT user_id FROM customer_profile WHERE customer_number='CUS-7'),2,'NO_SUPPLY','Water service follow-up','The customer reports intermittent water supply.','IN_PROGRESS','HIGH',?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 8 HOUR))", operator1);
        };
    }

    private long add(UserRepository users, PasswordEncoder encoder, JdbcTemplate jdbc, String email, PortalRole role, String name) {
        AppUser user = users.save(new AppUser(email, encoder.encode("Utility@123"), role, "ACTIVE"));
        switch (role) {
            case ADMIN -> jdbc.update("INSERT INTO admin_profile(user_id,full_name) VALUES (?,?)", user.getId(), name);
            case OPERATOR -> jdbc.update("INSERT INTO operator_profile(user_id,full_name,phone) VALUES (?,?,?)", user.getId(), name, "");
            case HELP_SUPPORT -> jdbc.update("INSERT INTO support_profile(user_id,full_name,phone) VALUES (?,?,?)", user.getId(), name, "");
            case CUSTOMER -> jdbc.update("INSERT INTO customer_profile(user_id,customer_number,full_name,approval_status) VALUES (?,?,?,?)", user.getId(), "CUS-" + user.getId(), name, "APPROVED");
        }
        return user.getId();
    }

    private void seedCustomerSample(JdbcTemplate jdbc, long customerId, int sequence) {
        int zone = ((sequence - 1) % 3) + 1;
        int typeId = sequence % 3 + 1;
        String cycle = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        for (int service = 1; service <= 3; service++) {
            String meter = "DEMO-" + service + "-" + sequence;
            jdbc.update("INSERT INTO connection(customer_id,service_id,connection_type_id,zone_id,meter_number,address,status) VALUES (?,?,?,?,?,?, 'ACTIVE')",
                    customerId, service, typeId, zone, meter, "" + (sequence * 12) + " Service Road, Zone " + zone);
            if (service == 1) {
                jdbc.update("INSERT INTO meter_reading(connection_id,previous_reading,current_reading,consumption,reading_date,cycle,recorded_by) VALUES ((SELECT id FROM connection WHERE meter_number=?),0,?,?,CURDATE(),?,?)",
                        meter, 45 + sequence * 7, 45 + sequence * 7, cycle, 2L);
                long reading = jdbc.queryForObject("SELECT id FROM meter_reading WHERE connection_id=(SELECT id FROM connection WHERE meter_number=?) AND cycle=?", Long.class, meter, cycle);
                long connection = jdbc.queryForObject("SELECT id FROM connection WHERE meter_number=?", Long.class, meter);
                double units = 45 + sequence * 7;
                double energy = units * 5.50;
                double tax = (energy + 45.00) * 0.05;
                String bill = "DEMO-BILL-" + sequence + "-" + cycle;
                jdbc.update("INSERT INTO bill(bill_number,connection_id,reading_id,cycle,issue_date,due_date,energy_charge,fixed_charge,tax_amount,total_amount,status) VALUES (?,?,?, ?,CURDATE(),DATE_ADD(CURDATE(),INTERVAL 15 DAY),?,?,?,?,'GENERATED')",
                        bill, connection, reading, cycle, energy, 45.00, tax, energy + 45.00 + tax);
            }
        }
    }
}
