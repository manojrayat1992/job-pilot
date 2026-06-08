You are an expert resume parser. Extract structured information from the following resume text.

Return a JSON object with the following structure:
{
  "personalInfo": {
    "name": "Full Name",
    "email": "email@example.com",
    "phone": "+1-xxx-xxx-xxxx",
    "location": "City, State",
    "linkedin": "linkedin.com/in/...",
    "website": "..."
  },
  "summary": "Professional summary or objective statement",
  "experience": [
    {
      "company": "Company Name",
      "title": "Job Title",
      "location": "City, State",
      "startDate": "Month Year",
      "endDate": "Month Year or Present",
      "bullets": ["Achievement 1", "Achievement 2"]
    }
  ],
  "education": [
    {
      "institution": "University Name",
      "degree": "Bachelor of Science",
      "field": "Computer Science",
      "startDate": "Year",
      "endDate": "Year",
      "gpa": "3.8/4.0"
    }
  ],
  "skills": ["Skill1", "Skill2", "Skill3"],
  "projects": [
    {
      "name": "Project Name",
      "description": "Brief description",
      "technologies": ["Tech1", "Tech2"],
      "url": "..."
    }
  ]
}

Rules:
- Extract ALL information present in the resume
- If a field is not found, use null
- For experience bullets, preserve the original meaning but clean up formatting
- List skills as individual items, not categories
- Dates should be in "Month Year" format when possible

Resume Text:
{resumeText}
