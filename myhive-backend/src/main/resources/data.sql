-- Sample data for local development
-- Tables are auto-created by Hibernate (ddl-auto=create-drop)

-- Insert sample destinations
INSERT INTO destinations (id, slug, name, description, country, city, image_url, rating, created_at)
VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'prague', 'Prague', 'The City of a Hundred Spires', 'Czech Republic',
        'Prague',
        'https://images.unsplash.com/photo-1541849546-216549ae216d?w=800&h=600&fit=crop', 4.75, CURRENT_TIMESTAMP),
       ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'tenerife', 'Tenerife', 'Volcanic adventures and beach parties',
        'Spain',
        'Santa Cruz de Tenerife', 'https://images.unsplash.com/photo-1594401708939-49f49fdf596a?w=800&h=600&fit=crop',
        4.60, CURRENT_TIMESTAMP),
       ('c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33', 'bali', 'Bali', 'Island of the Gods', 'Indonesia', 'Denpasar',
        'https://images.unsplash.com/photo-1537996194471-e657df975ab4?w=800&h=600&fit=crop', 4.85, CURRENT_TIMESTAMP),
       ('d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a44', 'dubai', 'Dubai', 'City of Gold', 'UAE', 'Dubai',
        'https://images.unsplash.com/photo-1512450837331-1991d975c66c?w=800&h=600&fit=crop', 4.70, CURRENT_TIMESTAMP),
       ('e4eebc99-9c0b-4ef8-bb6d-6bb9bd380a55', 'new-york', 'New York', 'The Concrete Jungle', 'USA', 'New York',
        'https://images.unsplash.com/photo-1496442226666-8d4d0e62e6e9?w=800&h=600&fit=crop', 4.80, CURRENT_TIMESTAMP);

-- Insert sample categories (mirrors the 27 canonical categories on prod after migration)
INSERT INTO categories (id, name, slug, created_at)
VALUES ('91111111-0000-0000-0000-000000000001', 'Nightlife', 'nightlife', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000002', 'Adventure', 'adventure', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000003', 'Daytime', 'daytime', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000004', 'Culture', 'culture', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000005', 'Action', 'action', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000006', 'Adult', 'adult', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000007', 'Dining', 'dining', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000008', 'Driving', 'driving', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000009', 'Food', 'food', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-00000000000a', 'Gaming', 'gaming', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-00000000000b', 'Luxury', 'luxury', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-00000000000c', 'Outdoor', 'outdoor', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-00000000000d', 'Party', 'party', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-00000000000e', 'Prank', 'prank', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-00000000000f', 'Rage', 'rage', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000010', 'Shooting', 'shooting', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000011', 'Show', 'show', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000012', 'Sightseeing', 'sightseeing', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000013', 'Social', 'social', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000014', 'Spa', 'spa', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000015', 'Sport', 'sport', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000016', 'Stag', 'stag', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000017', 'Strip', 'strip', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000018', 'Themed', 'themed', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-000000000019', 'Transfer', 'transfer', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-00000000001a', 'Water', 'water', CURRENT_TIMESTAMP),
       ('91111111-0000-0000-0000-00000000001b', 'Wellness', 'wellness', CURRENT_TIMESTAMP);

-- Insert sample activities for Tenerife
INSERT INTO activities (id, slug, destination_id, name, description, price, duration, image_url, includes,
                        created_at)
VALUES ('f5eebc99-9c0b-4ef8-bb6d-6bb9bd380a66', 'sunset-boat-party', 'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
        'Sunset Boat Party',
        'Dance the night away on a catamaran.', 50.00, 180,
        'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800&h=600&fit=crop',
        'Welcome drink, DJ, snacks, hotel pickup', CURRENT_TIMESTAMP),
       ('f6eebc99-9c0b-4ef8-bb6d-6bb9bd380a77', 'teide-national-park-tour', 'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
        'Teide National Park Tour',
        'Explore the stunning volcanic landscapes.', 45.00, 240,
        'https://images.unsplash.com/photo-1578662996442-48f60103fc96?w=800&h=600&fit=crop',
        'Transport, licensed guide, national park permit, water bottle', CURRENT_TIMESTAMP),
       ('f7eebc99-9c0b-4ef8-bb6d-6bb9bd380a88', 'jet-ski-adventure', 'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
        'Jet Ski Adventure',
        'High-speed fun on the ocean waves.', 70.00, 60,
        'https://images.unsplash.com/photo-1544197150-b99a580bb7a8?w=800&h=600&fit=crop',
        'Safety briefing, life jacket, instructor', CURRENT_TIMESTAMP),
       ('f8eebc99-9c0b-4ef8-bb6d-6bb9bd380a99', 'luxury-spa-session', 'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
        'Luxury Spa Session',
        'Relax and rejuvenate with a premium spa experience.', 90.00, 120,
        'https://images.unsplash.com/photo-1540555700478-4be289fbecef?w=800&h=600&fit=crop',
        'Full-body massage, sauna access, herbal tea, towels', CURRENT_TIMESTAMP);

-- Insert sample activities for Prague
INSERT INTO activities (id, slug, destination_id, name, description, price, duration, image_url, includes,
                        created_at)
VALUES ('f9eebc99-9c0b-4ef8-bb6d-6bb9bd380aaa', 'prague-castle-tour', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Prague Castle Tour',
        'Explore the largest ancient castle complex in the world.', 35.00, 180,
        'https://images.unsplash.com/photo-1500078974918-738828bc0422?w=800&h=600&fit=crop',
        'Skip-the-line tickets, licensed guide, audio headset', CURRENT_TIMESTAMP),
       ('faeebc99-9c0b-4ef8-bb6d-6bb9bd380abb', 'beer-tasting-experience', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Beer Tasting Experience',
        'Sample the finest Czech beers with a local guide.', 40.00, 120,
        'https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?w=800&h=600&fit=crop',
        '5 beer samples, local guide, snacks', CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000001', 'charles-bridge-walking-tour', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Charles Bridge Walking Tour',
        'Stroll across the iconic 14th-century bridge with a local historian.', 20.00, 90,
        'https://images.unsplash.com/photo-1541849546-216549ae216d?w=800&h=600&fit=crop',
        'Professional guide, city map', CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000002', 'vltava-river-cruise', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Vltava River Cruise',
        'Scenic river cruise with dinner and live jazz music.', 55.00, 150,
        'https://images.unsplash.com/photo-1592906209472-a36b1f3782ef?w=800&h=600&fit=crop',
        'Buffet dinner, welcome drink, live jazz band', CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000003', 'old-town-square-tour', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Old Town Square Tour',
        'Discover the astronomical clock, Tyn Church, and hidden courtyards.', 15.00, 120,
        'https://images.unsplash.com/photo-1458150945447-7fb764c11a92?w=800&h=600&fit=crop',
        NULL, CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000004', 'prague-pub-crawl', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Prague Pub Crawl',
        'Hit 5 bars and clubs in one epic night with free drinks at each stop.', 25.00, 240,
        'https://images.unsplash.com/photo-1575037614876-c38a4c44f5b8?w=800&h=600&fit=crop',
        '1 free drink at each bar, VIP club entry, party guide', CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000005', 'kayaking-on-the-vltava', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Kayaking on the Vltava',
        'Paddle through the heart of Prague with stunning castle views.', 38.00, 120,
        'https://images.unsplash.com/photo-1472745942893-4b9f730c7668?w=800&h=600&fit=crop',
        'Kayak, paddle, life jacket, waterproof bag, instructor', CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000006', 'czech-cooking-class', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Czech Cooking Class',
        'Learn to cook traditional trdelnik, svickova, and Czech dumplings.', 60.00, 180,
        'https://images.unsplash.com/photo-1556910103-1c02745aae4d?w=800&h=600&fit=crop',
        'All ingredients, recipe booklet, apron, meal with wine', CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000007', 'petrin-hill-hike', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Petrin Hill Hike',
        'Hike up to the Petrin Tower for panoramic views of the city.', 10.00, 90,
        'https://images.unsplash.com/photo-1562008675-4a1c1e5e5e08?w=800&h=600&fit=crop',
        NULL, CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000008', 'absinth-bar-experience', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Absinth Bar Experience',
        'Taste authentic Czech absinth in a hidden underground bar.', 30.00, 90,
        'https://images.unsplash.com/photo-1470337458703-46ad1756a187?w=800&h=600&fit=crop',
        '3 absinth tastings, guide', CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000009', 'segway-city-tour', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Segway City Tour',
        'Glide through Prague on a Segway covering all major landmarks.', 45.00, 120,
        'https://images.unsplash.com/photo-1558618666-fcd25c85f82e?w=800&h=600&fit=crop',
        'Segway rental, helmet, guide, training session', CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000010', 'jewish-quarter-walk', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Jewish Quarter Walk',
        'Explore the historic synagogues and the Old Jewish Cemetery.', 22.00, 120,
        'https://images.unsplash.com/photo-1513622470522-26c3c8a854bc?w=800&h=600&fit=crop',
        'Licensed guide, synagogue entry tickets', CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000011', 'rooftop-jazz-night', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Rooftop Jazz Night',
        'Live jazz performance with cocktails on a rooftop overlooking the city.', 35.00, 150,
        'https://images.unsplash.com/photo-1415201364774-f6f0bb35f28f?w=800&h=600&fit=crop',
        '2 cocktails, reserved seating', CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000012', 'e-scooter-adventure', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'E-Scooter Adventure',
        'Zip around Prague on an electric scooter with a guided route.', 28.00, 90,
        'https://images.unsplash.com/photo-1558981806-ec527fa84c39?w=800&h=600&fit=crop',
        'E-scooter rental, helmet, guided route map', CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000013', 'botanical-garden-visit', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Botanical Garden Visit',
        'Relax in the peaceful Troja Botanical Garden with tropical greenhouses.', 8.00, 120,
        'https://images.unsplash.com/photo-1585320806297-9794b3e4eeae?w=800&h=600&fit=crop',
        NULL, CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000014', 'underground-bunker-tour', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Underground Bunker Tour',
        'Descend into Cold War-era nuclear bunkers beneath the city.', 32.00, 90,
        'https://images.unsplash.com/photo-1562159278-1253a58da141?w=800&h=600&fit=crop',
        'Guided tour, flashlight, historical briefing', CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000015', 'nightclub-vip-experience', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Nightclub VIP Experience',
        'VIP entry and table service at Karlovy Lazne, the largest club in Central Europe.', 75.00, 300,
        'https://images.unsplash.com/photo-1566737236500-c8ac43014a67?w=800&h=600&fit=crop',
        'VIP entry, reserved table, bottle of prosecco, queue skip', CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000016', 'street-art-walking-tour', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Street Art Walking Tour',
        'Discover hidden murals and graffiti in the Zizkov and Karlin neighborhoods.', 18.00, 120,
        'https://images.unsplash.com/photo-1499781350541-7783f6c6a0c8?w=800&h=600&fit=crop',
        'Local artist guide, neighborhood map', CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000017', 'hot-air-balloon-ride', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Hot Air Balloon Ride',
        'Soar above the Bohemian countryside at sunrise.', 180.00, 180,
        'https://images.unsplash.com/photo-1507608616759-54f48f0af0ee?w=800&h=600&fit=crop',
        'Flight, champagne toast, flight certificate, hotel transfer', CURRENT_TIMESTAMP),
       ('aa000000-0000-0000-0000-000000000018', 'wine-tasting-evening', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'Wine Tasting Evening',
        'Discover Moravian wines in a candlelit medieval cellar.', 42.00, 120,
        'https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?w=800&h=600&fit=crop',
        '6 wine samples, cheese platter, sommelier guide', CURRENT_TIMESTAMP);

-- Link activities to categories
INSERT INTO activity_categories (activity_id, category_id)
VALUES ('f5eebc99-9c0b-4ef8-bb6d-6bb9bd380a66',
        '91111111-0000-0000-0000-000000000001'),                                         -- Sunset Boat Party → Nightlife
       ('f6eebc99-9c0b-4ef8-bb6d-6bb9bd380a77', '91111111-0000-0000-0000-000000000002'), -- Teide → Adventure
       ('f7eebc99-9c0b-4ef8-bb6d-6bb9bd380a88', '91111111-0000-0000-0000-000000000002'), -- Jet Ski → Adventure
       ('f8eebc99-9c0b-4ef8-bb6d-6bb9bd380a99', '91111111-0000-0000-0000-000000000003'), -- Spa → Daytime
       ('f9eebc99-9c0b-4ef8-bb6d-6bb9bd380aaa', '91111111-0000-0000-0000-000000000004'), -- Prague Castle → Culture
       ('faeebc99-9c0b-4ef8-bb6d-6bb9bd380abb', '91111111-0000-0000-0000-000000000001'), -- Beer Tasting → Nightlife
       ('aa000000-0000-0000-0000-000000000001', '91111111-0000-0000-0000-000000000003'), -- Charles Bridge → Daytime
       ('aa000000-0000-0000-0000-000000000002', '91111111-0000-0000-0000-000000000001'), -- Vltava Cruise → Nightlife
       ('aa000000-0000-0000-0000-000000000003', '91111111-0000-0000-0000-000000000003'), -- Old Town Square → Daytime
       ('aa000000-0000-0000-0000-000000000004', '91111111-0000-0000-0000-000000000001'), -- Pub Crawl → Nightlife
       ('aa000000-0000-0000-0000-000000000005', '91111111-0000-0000-0000-000000000002'), -- Kayaking → Adventure
       ('aa000000-0000-0000-0000-000000000006', '91111111-0000-0000-0000-000000000003'), -- Cooking Class → Daytime
       ('aa000000-0000-0000-0000-000000000007', '91111111-0000-0000-0000-000000000002'), -- Petrin Hike → Adventure
       ('aa000000-0000-0000-0000-000000000008', '91111111-0000-0000-0000-000000000001'), -- Absinth Bar → Nightlife
       ('aa000000-0000-0000-0000-000000000009', '91111111-0000-0000-0000-000000000002'), -- Segway → Adventure
       ('aa000000-0000-0000-0000-000000000010', '91111111-0000-0000-0000-000000000003'), -- Jewish Quarter → Daytime
       ('aa000000-0000-0000-0000-000000000011', '91111111-0000-0000-0000-000000000001'), -- Rooftop Jazz → Nightlife
       ('aa000000-0000-0000-0000-000000000012', '91111111-0000-0000-0000-000000000002'), -- E-Scooter → Adventure
       ('aa000000-0000-0000-0000-000000000013', '91111111-0000-0000-0000-000000000003'), -- Botanical Garden → Daytime
       ('aa000000-0000-0000-0000-000000000014',
        '91111111-0000-0000-0000-000000000002'),                                         -- Underground Bunker → Adventure
       ('aa000000-0000-0000-0000-000000000015', '91111111-0000-0000-0000-000000000001'), -- Nightclub VIP → Nightlife
       ('aa000000-0000-0000-0000-000000000016', '91111111-0000-0000-0000-000000000003'), -- Street Art → Daytime
       ('aa000000-0000-0000-0000-000000000017', '91111111-0000-0000-0000-000000000002'), -- Balloon Ride → Adventure
       ('aa000000-0000-0000-0000-000000000018', '91111111-0000-0000-0000-000000000001');
-- Wine Tasting → Nightlife

-- Insert sample bookings for testing admin dashboard
INSERT INTO bookings (id, user_email, stripe_session_id, total_amount, status, created_at, paid_at,
                      customer_name, phone, number_of_travelers, start_date, end_date, notes,
                      consultation_requested)
VALUES ('10000000-0000-0000-0000-000000000001', 'john.doe@example.com', 'cs_test_a1b2c3d4e5f6', 145.00, 'PAID',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
        'John Doe', '+44 7700 900123', 2, '2026-04-10', '2026-04-15',
        'Special requirements: Vegetarian meals | Contact method: email', false),
       ('10000000-0000-0000-0000-000000000002', 'jane.smith@example.com', 'cs_test_g7h8i9j0k1l2', 90.00, 'CONFIRMED',
        CURRENT_TIMESTAMP, NULL,
        'Jane Smith', '+34 612 345 678', 4, '2026-05-01', '2026-05-07',
        'Special requirements: Wheelchair accessible | Contact method: whatsapp', false),
       ('10000000-0000-0000-0000-000000000003', 'bob.wilson@example.com', 'cs_test_m3n4o5p6q7r8', 120.00, 'PENDING',
        CURRENT_TIMESTAMP, NULL,
        'Bob Wilson', '+1 555 234 5678', 1, '2026-06-20', '2026-06-25',
        'Special requirements: None | Contact method: phone', false);

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

-- Insert sample blog posts
INSERT INTO blog_posts (id, slug, title, excerpt, content, category, image_url, date, created_at)
VALUES ('30000000-0000-0000-0000-000000000001', 'top-5-group-travel-destinations-for-2026',
        'Top 5 Group Travel Destinations for 2026',
        'From the cobblestone streets of Prague to the volcanic landscapes of Tenerife, discover the hottest destinations for your next group adventure.',
        'Planning a group trip can feel overwhelming, but choosing the right destination makes all the difference. We''ve rounded up the five best spots for group adventures in 2026, based on activity variety, affordability, and group-friendliness.
Prague continues to reign as a top pick for groups. With its stunning architecture, vibrant nightlife, and incredibly affordable prices, it offers something for every type of traveler. Walking tours through the Old Town, river cruises on the Vltava, and pub crawls through centuries-old beer halls make it ideal for groups of any size.
Tenerife brings volcanic landscapes, beach parties, and year-round sunshine. Groups love the mix of adventure activities like hiking Mount Teide and relaxing on black sand beaches. The island''s diverse microclimates mean you can find the perfect weather no matter when you visit.
Bali remains the ultimate group retreat destination. From shared villas with private pools to group surfing lessons and temple tours, the Island of the Gods delivers unforgettable shared experiences at prices that won''t break the bank.
Dubai offers luxury group experiences like no other city. Desert safaris, rooftop dining, and water parks create the perfect blend of relaxation and adventure. Group packages at many attractions make it more affordable than you''d think.
New York City rounds out our list with its endless entertainment options. Broadway shows, food tours through diverse neighborhoods, and iconic landmarks ensure every member of your group finds something they love.',
        'Destinations',
        'https://images.unsplash.com/photo-1519677100203-a0e668c92439?w=1200&h=500&fit=crop',
        '2026-03-15', CURRENT_TIMESTAMP),

       ('30000000-0000-0000-0000-000000000002', 'how-to-plan-a-stress-free-group-trip',
        'How to Plan a Stress-Free Group Trip',
        'Coordinating schedules, budgets, and preferences for a group can be overwhelming. Here are our top tips to keep everyone happy.',
        'Group travel should be about making memories, not managing logistics. Yet too often, the planning process becomes a source of friction. Here''s how to keep things smooth from start to finish.
Start by establishing a shared budget early. Money is the number one source of group travel conflict. Use a shared document or app where everyone can input their comfort level, then plan activities that fit within the group''s range.
Designate a trip leader, but distribute responsibilities. One person shouldn''t carry the weight of all decisions. Assign roles: someone handles accommodation research, another looks into activities, and another manages transportation.
Build in free time. Not every minute needs to be scheduled. Some of the best group travel moments happen spontaneously. Plan key activities together but leave gaps for people to explore on their own or in smaller groups.
Use technology to your advantage. Tools like Trivlu''s Trip Builder let everyone browse and vote on activities, making group decision-making democratic and fun rather than chaotic.
Finally, set expectations early about communication. Create a single group chat, agree on response times for decisions, and establish a deadline for final commitments. Clear communication prevents last-minute surprises.',
        'Tips',
        'https://images.unsplash.com/photo-1539635278303-d4002c07eae3?w=1200&h=500&fit=crop',
        '2026-03-08', CURRENT_TIMESTAMP),

       ('30000000-0000-0000-0000-000000000003', 'why-ai-is-changing-the-way-we-travel-together',
        'Why AI is Changing the Way We Travel Together',
        'Artificial intelligence is transforming group travel planning from a logistical nightmare into a seamless experience. Here''s how.',
        'Artificial intelligence is no longer just a buzzword in travel — it''s fundamentally reshaping how groups plan, book, and experience trips together.
Traditional group travel planning involved endless back-and-forth messages, spreadsheets for tracking preferences, and compromise after compromise. AI changes this by analyzing everyone''s preferences simultaneously and suggesting itineraries that maximize group satisfaction.
Smart recommendation engines can now understand that while half the group wants adventure and the other half wants relaxation, a destination like Bali offers both. These algorithms consider budget constraints, travel dates, and even dietary preferences to create personalized group experiences.
Real-time pricing optimization is another game-changer. AI monitors flight and accommodation prices across hundreds of platforms, alerting groups to the best time to book and finding deals that fit everyone''s budget.
Language barriers are dissolving too. AI-powered translation tools mean groups can confidently explore destinations where they don''t speak the local language, opening up a world of off-the-beaten-path experiences.
At Trivlu, we''re building the future of multi-traveler experiences. Our AI-powered trip builder doesn''t just find activities — it creates cohesive itineraries that bring groups closer together while respecting individual preferences.',
        'Technology',
        'https://images.unsplash.com/photo-1488646953014-85cb44e25828?w=1200&h=500&fit=crop',
        '2026-02-28', CURRENT_TIMESTAMP),

       ('30000000-0000-0000-0000-000000000004', 'bali-on-a-budget-a-group-travel-guide',
        'Bali on a Budget: A Group Travel Guide',
        'Think Bali is too expensive for a group getaway? Think again. We break down how to experience the Island of the Gods without breaking the bank.',
        'Bali has a reputation as a luxury destination, but savvy groups can experience the magic of the Island of the Gods without spending a fortune. Here''s your complete budget guide.
Accommodation is where groups save the most. Instead of individual hotel rooms, rent a shared villa. A stunning 4-bedroom villa with a private pool can cost as little as €30 per person per night when split among a group of 8. Areas like Canggu and Ubud offer the best value.
Eat like a local and your food budget shrinks dramatically. Warungs (local restaurants) serve incredible Indonesian dishes for €2-3 per meal. Save the fancy restaurants for one or two special group dinners, and cook group breakfasts at your villa.
Transportation in Bali is affordable when shared. Hire a private driver for the day for around €30-40, split among your group. This gives you flexibility to explore temples, rice terraces, and beaches at your own pace.
Many of Bali''s best experiences are free or nearly free. Watch the sunset at Tanah Lot temple, explore the Tegallalang rice terraces, walk through the Ubud Monkey Forest, or simply spend the day at one of the island''s beautiful beaches.
For activities, look for group discounts. White water rafting, snorkeling trips, and cooking classes often offer lower per-person rates for groups of 6 or more. Book through your accommodation for the best deals.',
        'Guides',
        'https://images.unsplash.com/photo-1537996194471-e657df975ab4?w=1200&h=500&fit=crop',
        '2026-02-20', CURRENT_TIMESTAMP),

       ('30000000-0000-0000-0000-000000000005', 'the-rise-of-multi-traveler-experiences',
        'The Rise of Multi-Traveler Experiences',
        'Solo travel had its moment. Now, shared experiences are taking center stage. Explore why traveling with others is the new trend.',
        'For years, solo travel dominated the conversation. Instagram feeds were filled with lone adventurers at exotic destinations, and "finding yourself" through solo exploration became a cultural mantra. But the tide is turning.
Post-pandemic, people are craving connection more than ever. The rise of remote work means friend groups are scattered across cities and countries, making intentional group trips more meaningful — and more necessary — than before.
Multi-traveler experiences go beyond traditional group tours. They''re customizable, flexible adventures where the group shapes the itinerary rather than following a rigid schedule. Think shared villa stays with curated activity menus, not bus tours with numbered stickers.
The economics make sense too. Sharing accommodation, transportation, and group activity rates means everyone gets a better experience for less money. A luxury villa split eight ways often costs less than a mid-range hotel room.
Social media is evolving to reflect this shift. Shared photo albums, group travel accounts, and collaborative content creation are becoming the new norm. The best travel stories aren''t solo anymore — they''re collective.
This is exactly why we built Trivlu. The tools for solo travel planning are everywhere, but platforms designed specifically for group coordination were virtually nonexistent. We''re changing that.',
        'Trends',
        'https://images.unsplash.com/photo-1530789253388-582c481c54b0?w=1200&h=500&fit=crop',
        '2026-02-12', CURRENT_TIMESTAMP);

-- Insert sample packages
INSERT INTO packages (id, slug, name, description, image_url, includes, duration, discount_pct, destination_id, created_at)
VALUES
    ('bb000000-0000-0000-0000-000000000001',
     'prague-city-highlights',
     'Prague City Highlights',
     'The perfect introduction to Prague: explore the legendary castle complex, stroll across the iconic 14th-century bridge, and wander the labyrinthine Old Town Square. Three of the city''s most iconic sights, all in one day.',
     'https://images.unsplash.com/photo-1500078974918-738828bc0422?w=800&h=600&fit=crop',
     'Skip-the-line tickets, licensed guides, audio headsets, city map',
     390,
     10.00,
     'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
     CURRENT_TIMESTAMP),

    ('bb000000-0000-0000-0000-000000000002',
     'prague-pub-nights',
     'Prague Pub Nights',
     'Prague''s nightlife is legendary — and this package makes sure you don''t miss a drop. Kick off with a guided craft beer tasting, hit five bars on the epic pub crawl, and finish underground with a proper absinth ceremony. One night, three experiences, zero regrets.',
     'https://images.unsplash.com/photo-1575037614876-c38a4c44f5b8?w=800&h=600&fit=crop',
     '5 beer samples, 1 free drink per bar on pub crawl, VIP club entry, 3 absinth tastings, party guides',
     450,
     15.00,
     'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
     CURRENT_TIMESTAMP),

    ('bb000000-0000-0000-0000-000000000003',
     'tenerife-sea-and-adrenaline',
     'Tenerife Sea & Adrenaline',
     'The Atlantic at full throttle. Start with a high-speed jet ski session along the coast, then wind down on a sunset catamaran cruise with a DJ and welcome drinks. Sea, sun, and serious fun.',
     'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800&h=600&fit=crop',
     'Safety briefing, life jacket, jet ski instructor, catamaran welcome drink, DJ, snacks, hotel pickup',
     240,
     10.00,
     'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
     CURRENT_TIMESTAMP),

    ('bb000000-0000-0000-0000-000000000004',
     'tenerife-complete-experience',
     'Tenerife Complete Experience',
     'The full Tenerife package. Hike the otherworldly volcanic landscapes of Teide National Park in the morning, then get the adrenaline pumping on a jet ski in the afternoon. Round it all off with a full-body massage and sauna — you''ve earned it.',
     'https://images.unsplash.com/photo-1594401708939-49f49fdf596a?w=800&h=600&fit=crop',
     'Licensed guide, national park permit, water bottle, safety briefing, life jacket, full-body massage, sauna access, herbal tea',
     420,
     20.00,
     'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
     CURRENT_TIMESTAMP);

-- Link packages to activities (package_id, activity_id, position)
INSERT INTO package_activities (package_id, activity_id, position)
VALUES
    -- Prague City Highlights: Castle → Charles Bridge → Old Town Square
    ('bb000000-0000-0000-0000-000000000001', 'f9eebc99-9c0b-4ef8-bb6d-6bb9bd380aaa', 0),
    ('bb000000-0000-0000-0000-000000000001', 'aa000000-0000-0000-0000-000000000001', 1),
    ('bb000000-0000-0000-0000-000000000001', 'aa000000-0000-0000-0000-000000000003', 2),

    -- Prague Pub Nights: Beer Tasting → Pub Crawl → Absinth Bar
    ('bb000000-0000-0000-0000-000000000002', 'faeebc99-9c0b-4ef8-bb6d-6bb9bd380abb', 0),
    ('bb000000-0000-0000-0000-000000000002', 'aa000000-0000-0000-0000-000000000004', 1),
    ('bb000000-0000-0000-0000-000000000002', 'aa000000-0000-0000-0000-000000000008', 2),

    -- Tenerife Sea & Adrenaline: Jet Ski → Sunset Boat Party
    ('bb000000-0000-0000-0000-000000000003', 'f7eebc99-9c0b-4ef8-bb6d-6bb9bd380a88', 0),
    ('bb000000-0000-0000-0000-000000000003', 'f5eebc99-9c0b-4ef8-bb6d-6bb9bd380a66', 1),

    -- Tenerife Complete Experience: Teide Tour → Jet Ski → Luxury Spa
    ('bb000000-0000-0000-0000-000000000004', 'f6eebc99-9c0b-4ef8-bb6d-6bb9bd380a77', 0),
    ('bb000000-0000-0000-0000-000000000004', 'f7eebc99-9c0b-4ef8-bb6d-6bb9bd380a88', 1),
    ('bb000000-0000-0000-0000-000000000004', 'f8eebc99-9c0b-4ef8-bb6d-6bb9bd380a99', 2);

-- Link packages to categories
INSERT INTO package_categories (package_id, category_id)
VALUES
    ('bb000000-0000-0000-0000-000000000001', '91111111-0000-0000-0000-000000000004'), -- Prague Highlights → Culture
    ('bb000000-0000-0000-0000-000000000001', '91111111-0000-0000-0000-000000000003'), -- Prague Highlights → Daytime
    ('bb000000-0000-0000-0000-000000000002', '91111111-0000-0000-0000-000000000001'), -- Prague Nights → Nightlife
    ('bb000000-0000-0000-0000-000000000002', '91111111-0000-0000-0000-000000000013'), -- Prague Nights → Social
    ('bb000000-0000-0000-0000-000000000003', '91111111-0000-0000-0000-000000000002'), -- Tenerife Sea → Adventure
    ('bb000000-0000-0000-0000-000000000003', '91111111-0000-0000-0000-000000000001'), -- Tenerife Sea → Nightlife
    ('bb000000-0000-0000-0000-000000000004', '91111111-0000-0000-0000-000000000002'), -- Tenerife Complete → Adventure
    ('bb000000-0000-0000-0000-000000000004', '91111111-0000-0000-0000-000000000014'); -- Tenerife Complete → Spa

-- Insert sample blog posts
INSERT INTO blog_posts (id, slug, title, excerpt, content, category, image_url, date, created_at)
VALUES ('30000000-0000-0000-0000-000000000006', 'weekend-getaway-ideas-for-large-groups',
        'Weekend Getaway Ideas for Large Groups',
        'Planning a weekend escape for 10+ people? These destinations and activities are perfect for big groups looking for adventure.',
        'Planning a weekend escape for 10 or more people presents unique challenges, but the payoff is worth it. Here are our favorite destinations and formats for large group getaways.
Country house rentals are the gold standard for large group weekends. Platforms now offer properties that sleep 15-20 people, complete with games rooms, hot tubs, and large kitchens. The shared living spaces create natural gathering points while private bedrooms offer retreat.
Activity-focused weekends work brilliantly for large groups. Book a group surfing weekend, a wine tasting tour, or a cooking retreat. Having a shared activity gives the weekend structure without feeling overly planned.
City breaks can work for large groups if you plan smart. Book connected rooms or apartments in the same building, choose a neighborhood with plenty of restaurant options, and plan one or two group activities while leaving time for people to explore in smaller clusters.
Festival weekends are perfect for large groups. Music festivals, food festivals, and cultural events provide built-in entertainment and a shared experience that bonds the group. Book accommodation early and establish a meeting point for easy regrouping.
Whatever format you choose, the key is balance. Large groups need a mix of together time and freedom. Plan the must-do moments as a full group, but let natural sub-groups form for meals and downtime. The best large group trips feel effortless — even when they take careful planning behind the scenes.',
        'Destinations',
        'https://images.unsplash.com/photo-1506197603052-3cc9c3a201bd?w=1200&h=500&fit=crop',
        '2026-02-05', CURRENT_TIMESTAMP);

-- Quiz: 2 sample questions for Prague (dev only)
INSERT INTO quiz_questions (id, destination_id, prompt, sort_order, created_at)
VALUES ('d0000000-0000-0000-0000-000000000001', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Daytime hero or 4am legend?', 0, CURRENT_TIMESTAMP),
       ('d0000000-0000-0000-0000-000000000002', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Adrenaline rush or zero risk?', 1, CURRENT_TIMESTAMP);

INSERT INTO quiz_answers (id, question_id, label, sort_order)
VALUES ('d1000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', 'Daytime', 0),
       ('d1000000-0000-0000-0000-000000000002', 'd0000000-0000-0000-0000-000000000001', '4am legend', 1),
       ('d1000000-0000-0000-0000-000000000003', 'd0000000-0000-0000-0000-000000000002', 'Adrenaline', 0),
       ('d1000000-0000-0000-0000-000000000004', 'd0000000-0000-0000-0000-000000000002', 'Zero risk', 1);

INSERT INTO quiz_answer_weights (id, answer_id, category_id, weight)
VALUES ('d2000000-0000-0000-0000-000000000001', 'd1000000-0000-0000-0000-000000000001', '91111111-0000-0000-0000-000000000003', 2),
       ('d2000000-0000-0000-0000-000000000002', 'd1000000-0000-0000-0000-000000000002', '91111111-0000-0000-0000-000000000001', 2),
       ('d2000000-0000-0000-0000-000000000003', 'd1000000-0000-0000-0000-000000000003', '91111111-0000-0000-0000-000000000002', 2),
       ('d2000000-0000-0000-0000-000000000004', 'd1000000-0000-0000-0000-000000000004', '91111111-0000-0000-0000-000000000003', 2);

-- Featured activities shown on the homepage grid
UPDATE activities SET featured = TRUE WHERE slug IN (
    'prague-pub-crawl', 'beer-tasting-experience', 'absinth-bar-experience',
    'nightclub-vip-experience', 'rooftop-jazz-night', 'segway-city-tour',
    'e-scooter-adventure', 'kayaking-on-the-vltava', 'underground-bunker-tour',
    'hot-air-balloon-ride', 'jet-ski-adventure', 'sunset-boat-party'
);

-- Group-minimum example: sunset-boat-party requires a €600 minimum order
UPDATE activities SET min_price = 600.00 WHERE slug = 'sunset-boat-party';
