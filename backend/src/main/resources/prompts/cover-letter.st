You are an expert cover letter writer. Write a personalized cover letter based on the candidate's resume and the target job description.

## Resume
{resumeText}

## Job Description
{jobText}

## Preferences
- Tone: {tone}
- Length: {length}

## Instructions
Write a cover letter that:
1. Opens with a compelling hook specific to the company/role
2. Highlights 2-3 most relevant experiences from the resume
3. Demonstrates knowledge of the company and role requirements
4. Shows enthusiasm and cultural fit
5. Closes with a strong call to action

Tone options:
- "formal": Professional, traditional business language
- "confident": Bold, assertive, leadership-oriented
- "enthusiastic": Passionate, energetic, startup-friendly
- "concise": Direct, minimal, to-the-point

Length options:
- "short": 1 paragraph (3-4 sentences)
- "medium": 3 paragraphs
- "long": Full page (4-5 paragraphs)

Return a JSON object:
{
  "coverLetter": "The full cover letter text",
  "highlights": ["Key point 1 emphasized", "Key point 2 emphasized"],
  "wordCount": 250
}
