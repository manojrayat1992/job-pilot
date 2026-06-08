-- Create resume_templates table
CREATE TABLE resume_templates (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    thumbnail_url TEXT,
    layout TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Add template_id to resumes table
ALTER TABLE resumes ADD COLUMN template_id UUID REFERENCES resume_templates(id);

-- Insert some initial templates
INSERT INTO resume_templates (id, name, description, thumbnail_url, layout, is_active, created_at, updated_at)
VALUES 
(
    '00000000-0000-0000-0000-000000000001', 
    'Modern Professional', 
    'A clean, modern layout focused on readability and professional impact.', 
    'https://images.unsplash.com/photo-1586281380349-632531db7ed4?w=400&h=600&fit=crop', 
    'modern-professional', 
    true, 
    NOW(), 
    NOW()
),
(
    '00000000-0000-0000-0000-000000000002', 
    'Creative Minimalist', 
    'Elegant minimalist design with a creative touch, perfect for designers and tech roles.', 
    'https://images.unsplash.com/photo-1586281380117-5a60ae2050cc?w=400&h=600&fit=crop', 
    'creative-minimalist', 
    true, 
    NOW(), 
    NOW()
),
(
    '00000000-0000-0000-0000-000000000003', 
    'Executive Classic', 
    'Traditional high-density layout ideal for experienced professionals and executives.', 
    'https://images.unsplash.com/photo-1512486130939-2c4f79935e4f?w=400&h=600&fit=crop', 
    'executive-classic', 
    true, 
    NOW(), 
    NOW()
);
