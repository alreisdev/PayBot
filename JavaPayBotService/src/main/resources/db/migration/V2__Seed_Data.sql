-- Sample bills for user-1
-- Flyway migration V2

INSERT INTO bills (user_id, biller_name, bill_type, amount, due_date,
                   billing_period_start, billing_period_end, status, account_number)
VALUES
    ('user-1', 'City Electric Company', 'electricity', 125.50,
     CURRENT_DATE + INTERVAL '5 days', CURRENT_DATE - INTERVAL '1 month',
     CURRENT_DATE, 'PENDING', 'ACC-ELEC-001'),

    ('user-1', 'Metro Water Services', 'water', 45.00,
     CURRENT_DATE + INTERVAL '10 days', CURRENT_DATE - INTERVAL '1 month',
     CURRENT_DATE, 'PENDING', 'ACC-WATER-002'),

    ('user-1', 'FastNet Internet', 'internet', 79.99,
     CURRENT_DATE + INTERVAL '3 days', CURRENT_DATE - INTERVAL '1 month',
     CURRENT_DATE, 'PENDING', 'ACC-NET-003'),

    ('user-1', 'CityGas Inc', 'gas', 67.25,
     CURRENT_DATE + INTERVAL '15 days', CURRENT_DATE - INTERVAL '1 month',
     CURRENT_DATE, 'PENDING', 'ACC-GAS-004'),

    ('user-1', 'Mobile Plus', 'phone', 55.00,
     CURRENT_DATE + INTERVAL '7 days', CURRENT_DATE - INTERVAL '1 month',
     CURRENT_DATE, 'PENDING', 'ACC-PHONE-005');
