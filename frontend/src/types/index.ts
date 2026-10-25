export interface User {
  id: string;
  email: string;
  name: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

export interface JobApplication {
  id: string;
  company: string;
  position: string;
  status: 'SAVED' | 'APPLIED' | 'PHONE_SCREEN' | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'ACCEPTED' | 'WITHDRAWN';
  appliedDate?: string;
  notes?: string;
  coverLetter?: string;
  source?: string;
  contactName?: string;
  contactEmail?: string;
  priority?: string;
  jobId?: string;
  resumeId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ApplicationStats {
  total: number;
  byStatus: Record<string, number>;
}

export interface Resume {
  id: string;
  title: string;
  version: number;
  parentResumeId?: string;
  targetJobId?: string;
  active: boolean;
  fileName: string;
  fileType: string;
  atsScore?: number;
  atsIssues?: Record<string, any>;
  tags?: string[];
  rawText?: string;
  parsedJson?: Record<string, any>;
  templateId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ResumeTemplate {
  id: string;
  name: string;
  description: string;
  thumbnailUrl: string;
  layout: string;
  active: boolean;
}

export interface Job {
  id: string;
  title: string;
  company: string;
  location: string;
  sourceUrl?: string;
  experienceLevel?: string;
  jobType?: string;
  salaryRange?: string;
  skillsRequired?: string[];
  skillsNice?: string[];
  rawText?: string;
  createdAt: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface OptimizeRequest {
  resume: string;
  jobDescription: string;
}

export interface OptimizeResponse {
  optimizedResume: string;
  suggestions: string[];
}

export interface CoverLetterRequest {
  resume: string;
  jobDescription: string;
  companyName: string;
  position: string;
}

export interface CoverLetterResponse {
  coverLetter: string;
}

export interface AnswerRequest {
  question: string;
  jobDescription?: string;
  resume?: string;
}

export interface AnswerResponse {
  answer: string;
}
