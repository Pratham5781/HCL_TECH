INSERT INTO utility_service(code,name) VALUES ('ELECTRICITY','Electricity'),('WATER','Water'),('LPG','LPG');
INSERT INTO connection_type(name) VALUES ('RESIDENTIAL'),('COMMERCIAL'),('INDUSTRIAL');
INSERT INTO zone(name,description) VALUES ('Central','Central service zone'),('North','Northern service zone'),('South','Southern service zone');
INSERT INTO tariff_slab(service_id,connection_type_id,range_start,range_end,rate_per_unit,fixed_charge,tax_percent,late_fee_percent,due_days,effective_from)
SELECT s.id,t.id,0,100,CASE s.code WHEN 'ELECTRICITY' THEN 5.50 WHEN 'WATER' THEN 2.25 ELSE 850.00 END,CASE s.code WHEN 'ELECTRICITY' THEN 45.00 WHEN 'WATER' THEN 25.00 ELSE 0.00 END,5.00,2.00,15,'2025-01-01' FROM utility_service s CROSS JOIN connection_type t;
INSERT INTO tariff_slab(service_id,connection_type_id,range_start,range_end,rate_per_unit,fixed_charge,tax_percent,late_fee_percent,due_days,effective_from)
SELECT s.id,t.id,100.001,300,CASE s.code WHEN 'ELECTRICITY' THEN 7.25 WHEN 'WATER' THEN 3.50 ELSE 850.00 END,CASE s.code WHEN 'ELECTRICITY' THEN 45.00 WHEN 'WATER' THEN 25.00 ELSE 0.00 END,5.00,2.00,15,'2025-01-01' FROM utility_service s CROSS JOIN connection_type t;
INSERT INTO tariff_slab(service_id,connection_type_id,range_start,range_end,rate_per_unit,fixed_charge,tax_percent,late_fee_percent,due_days,effective_from)
SELECT s.id,t.id,300.001,NULL,CASE s.code WHEN 'ELECTRICITY' THEN 9.00 WHEN 'WATER' THEN 5.00 ELSE 850.00 END,CASE s.code WHEN 'ELECTRICITY' THEN 45.00 WHEN 'WATER' THEN 25.00 ELSE 0.00 END,5.00,2.00,15,'2025-01-01' FROM utility_service s CROSS JOIN connection_type t;

