You are an expert job description analyst. Extract structured information from the following job description.

Return a JSON object with the following structure:
{
  "title": "Job Title",
  "company": "Company Name",
  "location": "City, State or Remote",
  "experienceLevel": "Junior|Mid|Senior|Lead|Principal",
  "jobType": "Full-time|Part-time|Contract|Internship",
  "salaryRange": "$XXk - $XXXk or null if not mentioned",
  "requiredSkills": ["Skill1", "Skill2"],
  "niceToHaveSkills": ["Skill1", "Skill2"],
  "responsibilities": ["Responsibility 1", "Responsibility 2"],
  "qualifications": ["Qualification 1", "Qualification 2"],
  "benefits": ["Benefit 1", "Benefit 2"]
}

Rules:
- Distinguish between REQUIRED and NICE-TO-HAVE skills carefully
- Experience level should be inferred from years of experience mentioned and job title
- If salary is not mentioned, set salaryRange to null
- Extract ALL responsibilities and qualifications as separate bullet items
- Skills should be specific technologies/tools, not generic descriptions

Job Description:
{jobText}
