-- Sample data for local development
-- Tables are auto-created by Hibernate (ddl-auto=create-drop)

-- Insert sample destinations
INSERT INTO destinations (id, name, description, country, city, image_url, rating, created_at)
VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Prague', 'The City of a Hundred Spires', 'Czech Republic', 'Prague',
        'https://images.unsplash.com/photo-1541849546-216549ae216d?w=800&h=600&fit=crop', 4.75, CURRENT_TIMESTAMP),
       ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'Tenerife', 'Volcanic adventures and beach parties', 'Spain',
        'Santa Cruz de Tenerife', 'https://images.unsplash.com/photo-1594401708939-49f49fdf596a?w=800&h=600&fit=crop',
        4.60, CURRENT_TIMESTAMP),
       ('c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33', 'Bali', 'Island of the Gods', 'Indonesia', 'Denpasar',
        'https://images.unsplash.com/photo-1537996194471-e657df975ab4?w=800&h=600&fit=crop', 4.85, CURRENT_TIMESTAMP),
       ('d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a44', 'Dubai', 'City of Gold', 'UAE', 'Dubai',
        'https://images.unsplash.com/photo-1512450837331-1991d975c66c?w=800&h=600&fit=crop', 4.70, CURRENT_TIMESTAMP),
       ('e4eebc99-9c0b-4ef8-bb6d-6bb9bd380a55', 'New York', 'The Concrete Jungle', 'USA', 'New York',
        'https://images.unsplash.com/photo-1496442226666-8d4d0e62e6e9?w=800&h=600&fit=crop', 4.80, CURRENT_TIMESTAMP);

-- Insert sample activities for Tenerife
INSERT INTO activities (id, destination_id, name, description, price, duration, category, image_url, created_at)
VALUES ('f5eebc99-9c0b-4ef8-bb6d-6bb9bd380a66', 'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'Sunset Boat Party',
        'Dance the night away on a catamaran.', 50.00, 180, 'nightlife',
        'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800&h=600&fit=crop', CURRENT_TIMESTAMP),
       ('f6eebc99-9c0b-4ef8-bb6d-6bb9bd380a77', 'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'Teide National Park Tour',
        'Explore the stunning volcanic landscapes.', 45.00, 240, 'adventure',
        'https://images.unsplash.com/photo-1578662996442-48f60103fc96?w=800&h=600&fit=crop', CURRENT_TIMESTAMP),
       ('f7eebc99-9c0b-4ef8-bb6d-6bb9bd380a88', 'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'Jet Ski Adventure',
        'High-speed fun on the ocean waves.', 70.00, 60, 'adventure',
        'https://images.unsplash.com/photo-1544197150-b99a580bb7a8?w=800&h=600&fit=crop', CURRENT_TIMESTAMP),
       ('f8eebc99-9c0b-4ef8-bb6d-6bb9bd380a99', 'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'Luxury Spa Session',
        'Relax and rejuvenate with a premium spa experience.', 90.00, 120, 'daytime',
        'https://images.unsplash.com/photo-1540555700478-4be289fbecef?w=800&h=600&fit=crop', CURRENT_TIMESTAMP);

-- Insert sample activities for Prague
INSERT INTO activities (id, destination_id, name, description, price, duration, category, image_url, created_at)
VALUES ('f9eebc99-9c0b-4ef8-bb6d-6bb9bd380aaa', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Prague Castle Tour',
        'Explore the largest ancient castle complex in the world.', 35.00, 180, 'culture',
        'https://images.unsplash.com/photo-1500078974918-738828bc0422?w=800&h=600&fit=crop', CURRENT_TIMESTAMP),
       ('faeebc99-9c0b-4ef8-bb6d-6bb9bd380abb', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Beer Tasting Experience',
        'Sample the finest Czech beers with a local guide.', 40.00, 120, 'nightlife',
        'https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?w=800&h=600&fit=crop', CURRENT_TIMESTAMP);

-- Insert sample bookings for testing admin dashboard
INSERT INTO bookings (id, user_email, stripe_session_id, total_amount, status, created_at, paid_at)
VALUES ('10000000-0000-0000-0000-000000000001', 'john.doe@example.com', 'cs_test_a1b2c3d4e5f6', 145.00, 'PAID',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('10000000-0000-0000-0000-000000000002', 'jane.smith@example.com', 'cs_test_g7h8i9j0k1l2', 90.00, 'CONFIRMED',
        CURRENT_TIMESTAMP, NULL),
       ('10000000-0000-0000-0000-000000000003', 'bob.wilson@example.com', 'cs_test_m3n4o5p6q7r8', 120.00, 'PENDING',
        CURRENT_TIMESTAMP, NULL);

-- Insert booking items for the sample bookings
INSERT INTO booking_items (id, booking_id, activity_id, activity_name, destination_name, price, quantity)
VALUES ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
        'f5eebc99-9c0b-4ef8-bb6d-6bb9bd380a66', 'Sunset Boat Party', 'Tenerife', 50.00, 1),
       ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
        'f6eebc99-9c0b-4ef8-bb6d-6bb9bd380a77', 'Teide National Park Tour', 'Tenerife', 45.00, 1),
       ('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001',
        'f5eebc99-9c0b-4ef8-bb6d-6bb9bd380a66', 'Sunset Boat Party', 'Tenerife', 50.00, 1),
       ('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002',
        'f8eebc99-9c0b-4ef8-bb6d-6bb9bd380a99', 'Luxury Spa Session', 'Tenerife', 90.00, 1),
       ('20000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000003',
        'f9eebc99-9c0b-4ef8-bb6d-6bb9bd380aaa', 'Prague Castle Tour', 'Prague', 35.00, 1),
       ('20000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000003',
        'faeebc99-9c0b-4ef8-bb6d-6bb9bd380abb', 'Beer Tasting Experience', 'Prague', 40.00, 1),
       ('20000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000003',
        'f6eebc99-9c0b-4ef8-bb6d-6bb9bd380a77', 'Teide National Park Tour', 'Tenerife', 45.00, 1);
