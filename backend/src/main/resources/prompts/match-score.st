You are an expert ATS (Applicant Tracking System) analyst and career advisor. Score how well this resume matches the given job description.

## Resume
{resumeText}

## Job Description
{jobText}

## Instructions
Analyze the match between the resume and job description across these dimensions:

1. **Keyword Match** (0-100): How many key terms from the JD appear in the resume?
2. **Experience Match** (0-100): Does the candidate's experience level and domain match?
3. **Skills Match** (0-100): How many required/nice-to-have skills are present?

Return a JSON object:
{
  "overallScore": 75,
  "keywordMatchScore": 80,
  "experienceMatchScore": 70,
  "skillsMatchScore": 65,
  "matchedSkills": ["Java", "Spring Boot", "PostgreSQL"],
  "missingSkills": ["Kubernetes", "Terraform"],
  "suggestions": [
    "Add cloud deployment experience to your resume",
    "Highlight any CI/CD pipeline experience"
  ],
  "summary": "Your resume is a good match for this role, with strong backend development experience. Focus on adding cloud-native and DevOps keywords to improve your score."
}

Rules:
- Be honest and precise in scoring
- Overall score is a weighted average: keywords(30%) + experience(40%) + skills(30%)
- Suggestions should be specific and actionable
- Missing skills should only include truly important ones from the JD
