-- Initialize MyHive Database Schema with UUID-based structure

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create destinations table
CREATE TABLE destinations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    country VARCHAR(100),
    city VARCHAR(100),
    image_url VARCHAR(500),
    rating DECIMAL(3,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create activities table
CREATE TABLE activities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    destination_id UUID NOT NULL REFERENCES destinations(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    duration INT,
    category VARCHAR(100),
    image_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for activities
CREATE INDEX idx_activities_destination ON activities(destination_id);
CREATE INDEX idx_activities_category ON activities(category);

-- Create bookings table
CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_email VARCHAR(255),
    stripe_session_id VARCHAR(255) UNIQUE,
    total_amount DECIMAL(10,2),
    status VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP
);

-- Create booking_items table
CREATE TABLE booking_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    activity_id UUID NOT NULL REFERENCES activities(id),
    activity_name VARCHAR(255),
    destination_name VARCHAR(255),
    price DECIMAL(10,2),
    quantity INT
);

-- Create index for booking_items
CREATE INDEX idx_booking_items_booking ON booking_items(booking_id);

-- Insert sample destinations
INSERT INTO destinations (id, name, description, country, city, image_url, rating) VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
                                                                                           'Prague',
                                                                                           'The City of a Hundred Spires',
                                                                                           'Czech Republic', 'Prague',
                                                                                           'https://images.unsplash.com/photo-1541849546-216549ae216d?w=800&h=600&fit=crop',
                                                                                           4.75),
                                                                                          ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
                                                                                           'Tenerife',
                                                                                           'Volcanic adventures and beach parties',
                                                                                           'Spain',
                                                                                           'Santa Cruz de Tenerife',
                                                                                           'https://images.unsplash.com/photo-1594401708939-49f49fdf596a?w=800&h=600&fit=crop',
                                                                                           4.60),
                                                                                          ('c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33',
                                                                                           'Bali', 'Island of the Gods',
                                                                                           'Indonesia', 'Denpasar',
                                                                                           'https://images.unsplash.com/photo-1537996194471-e657df975ab4?w=800&h=600&fit=crop',
                                                                                           4.85),
                                                                                          ('d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a44',
                                                                                           'Dubai', 'City of Gold',
                                                                                           'UAE', 'Dubai',
                                                                                           'https://images.unsplash.com/photo-1512450837331-1991d975c66c?w=800&h=600&fit=crop',
                                                                                           4.70),
                                                                                          ('e4eebc99-9c0b-4ef8-bb6d-6bb9bd380a55',
                                                                                           'New York',
                                                                                           'The Concrete Jungle', 'USA',
                                                                                           'New York',
                                                                                           'https://images.unsplash.com/photo-1496442226666-8d4d0e62e6e9?w=800&h=600&fit=crop',
                                                                                           4.80);

-- Insert sample activities for Tenerife
INSERT INTO activities (id, destination_id, name, description, price, duration, category, image_url) VALUES ('f5eebc99-9c0b-4ef8-bb6d-6bb9bd380a66',
                                                                                                             'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
                                                                                                             'Sunset Boat Party',
                                                                                                             'Dance the night away on a catamaran.',
                                                                                                             50.00, 180,
                                                                                                             'nightlife',
                                                                                                             'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800&h=600&fit=crop'),
                                                                                                            ('f6eebc99-9c0b-4ef8-bb6d-6bb9bd380a77',
                                                                                                             'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
                                                                                                             'Teide National Park Tour',
                                                                                                             'Explore the stunning volcanic landscapes.',
                                                                                                             45.00, 240,
                                                                                                             'adventure',
                                                                                                             'https://images.unsplash.com/photo-1578662996442-48f60103fc96?w=800&h=600&fit=crop'),
                                                                                                            ('f7eebc99-9c0b-4ef8-bb6d-6bb9bd380a88',
                                                                                                             'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
                                                                                                             'Jet Ski Adventure',
                                                                                                             'High-speed fun on the ocean waves.',
                                                                                                             70.00, 60,
                                                                                                             'adventure',
                                                                                                             'https://images.unsplash.com/photo-1544197150-b99a580bb7a8?w=800&h=600&fit=crop'),
                                                                                                            ('f8eebc99-9c0b-4ef8-bb6d-6bb9bd380a99',
                                                                                                             'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
                                                                                                             'Luxury Spa Session',
                                                                                                             'Relax and rejuvenate with a premium spa experience.',
                                                                                                             90.00, 120,
                                                                                                             'daytime',
                                                                                                             'https://images.unsplash.com/photo-1540555700478-4be289fbecef?w=800&h=600&fit=crop');

-- Insert sample activities for Prague
INSERT INTO activities (id, destination_id, name, description, price, duration, category, image_url) VALUES ('f9eebc99-9c0b-4ef8-bb6d-6bb9bd380aaa',
                                                                                                             'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
                                                                                                             'Prague Castle Tour',
                                                                                                             'Explore the largest ancient castle complex in the world.',
                                                                                                             35.00, 180,
                                                                                                             'culture',
                                                                                                             'https://images.unsplash.com/photo-1500078974918-738828bc0422?w=800&h=600&fit=crop'),
                                                                                                            ('faeebc99-9c0b-4ef8-bb6d-6bb9bd380abb',
                                                                                                             'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
                                                                                                             'Beer Tasting Experience',
                                                                                                             'Sample the finest Czech beers with a local guide.',
                                                                                                             40.00, 120,
                                                                                                             'nightlife',
                                                                                                             'https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?w=800&h=600&fit=crop');
