# Job Pilot

AI-powered job application assistant that helps you optimize your resume, generate cover letters, prepare for interviews, and track your job applications.

## Overview

Job Pilot is a full-stack application leveraging AI to streamline your job search process. It includes a web application, browser extension, and backend API to provide a comprehensive job application toolkit.

## Features

- **Resume Optimizer**: AI-powered resume optimization tailored to specific job descriptions
- **Cover Letter Generator**: Generate personalized cover letters for job applications
- **Interview Preparation**: Practice interview questions with AI assistance
- **Job Application Tracking**: Manage and track all your job applications in one place
- **Browser Extension**: Quick access to AI tools while browsing job boards
- **Vector Search**: Semantic search for job descriptions and resumes using pgvector

## Tech Stack

### Backend
- **Spring Boot 3.3.5** - Java framework
- **Java 21** - Programming language
- **Spring AI** - OpenAI integration
- **PostgreSQL with pgvector** - Database with vector search capabilities
- **Redis** - Caching and session management
- **Spring Security + JWT** - Authentication and authorization
- **Apache Tika** - Document parsing
- **OpenPDF** - PDF generation
- **Playwright** - Headless browser automation
- **Stripe** - Payment processing
- **Flyway** - Database migrations

### Frontend
- **React 18** - UI library
- **TypeScript** - Type safety
- **Vite** - Build tool and dev server
- **TailwindCSS** - Styling
- **shadcn/ui** - UI components
- **React Router** - Client-side routing
- **Zustand** - State management
- **React Query** - Data fetching
- **Axios** - HTTP client

### Browser Extension
- **Chrome Extension** - Browser extension for quick access to AI tools

## Project Structure

```
job-pilot/
├── backend/                 # Spring Boot backend API
│   ├── src/
│   │   └── main/
│   │       ├── java/       # Java source code
│   │       └── resources/  # Configuration files
│   └── pom.xml            # Maven dependencies
├── frontend/               # React + TypeScript frontend
│   ├── src/
│   │   ├── components/    # React components
│   │   ├── pages/         # Page components
│   │   ├── lib/           # Utilities and API client
│   │   └── store/         # Zustand stores
│   └── package.json       # Node dependencies
├── extension/              # Browser extension
│   ├── manifest.json      # Extension manifest
│   ├── content.js         # Content script
│   ├── background.js      # Background script
│   └── popup.html         # Extension popup
├── docker/                 # Docker configurations
│   └── docker-compose.yml # Development services
├── .env.example           # Environment variables template
└── README.md              # This file
```

## Prerequisites

- **Java 21** - For backend development
- **Node.js 18+** - For frontend development
- **Maven 3.8+** - For backend build
- **Docker & Docker Compose** - For running PostgreSQL and Redis
- **OpenAI API Key** - Required for AI features
- **SerpAPI Key** - Required for job search (optional)

## Getting Started

### 1. Clone the Repository

```bash
git clone <repository-url>
cd job-pilot
```

### 2. Set Up Environment Variables

Copy the example environment file and fill in your values:

```bash
cp .env.example .env
```

Edit `.env` and provide:
- `OPENAI_API_KEY` - Your OpenAI API key
- `SERPAPI_KEY` - Your SerpAPI key (optional)
- Database credentials (or use defaults)
- JWT secret (generate a secure random string)

### 3. Start Infrastructure Services

Start PostgreSQL and Redis using Docker Compose:

```bash
cd docker
docker-compose up -d
```

This will start:
- PostgreSQL on port 5432 (with pgvector extension)
- Redis on port 6379

### 4. Backend Setup

Navigate to the backend directory:

```bash
cd backend
```

Build and run the backend:

```bash
mvn clean install
mvn spring-boot:run
```

The backend API will be available at `http://localhost:8080`

API documentation (Swagger UI) is available at `http://localhost:8080/swagger-ui.html`

### 5. Frontend Setup

Navigate to the frontend directory:

```bash
cd frontend
```

Install dependencies:

```bash
npm install
```

Start the development server:

```bash
npm run dev
```

The frontend will be available at `http://localhost:5173`

### 6. Browser Extension (Optional)

Load the extension in Chrome:
1. Open Chrome and navigate to `chrome://extensions/`
2. Enable "Developer mode"
3. Click "Load unpacked"
4. Select the `extension` directory

## Configuration

### Backend Configuration

Backend configuration is in `backend/src/main/resources/application-dev.yml`:

- Database connection settings
- Redis connection settings
- OpenAI API configuration
- JWT settings
- CORS origins

### Frontend Configuration

Frontend environment variables (create `.env` in frontend directory):

```env
VITE_API_URL=http://localhost:8080
```

## API Endpoints

Key API endpoints (base URL: `http://localhost:8080/api`):

- `POST /auth/register` - User registration
- `POST /auth/login` - User login
- `POST /auth/refresh` - Refresh JWT token
- `POST /ai/resume-optimize` - Optimize resume
- `POST /ai/cover-letter` - Generate cover letter
- `POST /ai/interview-prep` - Interview preparation
- `GET /applications` - List job applications
- `POST /applications` - Create job application
- `PUT /applications/{id}` - Update job application
- `DELETE /applications/{id}` - Delete job application

For complete API documentation, visit `http://localhost:8080/swagger-ui.html`

## Database Migrations

Database migrations are managed with Flyway. Migration files are located in:
`backend/src/main/resources/db/migration/`

To run migrations:
```bash
cd backend
mvn flyway:migrate
```

## Development

### Running Tests

Backend tests:
```bash
cd backend
mvn test
```

Frontend tests:
```bash
cd frontend
npm run test
```

### Code Style

- Backend: Follow Java coding conventions
- Frontend: ESLint and Prettier configured
- Use meaningful commit messages

## Deployment

### Backend Deployment

Build the JAR file:
```bash
cd backend
mvn clean package -DskipTests
```

The JAR file will be in `backend/target/job-pilot-backend-0.1.0-SNAPSHOT.jar`

Run with:
```bash
java -jar target/job-pilot-backend-0.1.0-SNAPSHOT.jar
```

### Frontend Deployment

Build for production:
```bash
cd frontend
npm run build
```

The built files will be in `frontend/dist/`

Deploy the `dist` folder to your web server or hosting platform.

## Troubleshooting

### Database Connection Issues

Ensure PostgreSQL is running:
```bash
docker-compose ps
```

Check logs:
```bash
docker-compose logs postgres
```

### Redis Connection Issues

Ensure Redis is running:
```bash
docker-compose ps
```

### OpenAI API Errors

Verify your API key is set correctly in `.env`:
```bash
OPENAI_API_KEY=sk-...
```

### Port Conflicts

If ports are already in use, modify the ports in:
- `docker/docker-compose.yml` (for PostgreSQL/Redis)
- `backend/src/main/resources/application-dev.yml` (for backend)
- Frontend `.env` (for frontend API URL)

## Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Write tests for new features
5. Submit a pull request

## License

MIT License - see LICENSE file for details

## Support

For issues and questions:
- Open an issue on GitHub
- Check existing documentation
- Review API documentation at `/swagger-ui.html`

## Acknowledgments

- Spring AI team for the AI integration framework
- OpenAI for GPT API
- pgvector team for vector database extension
- shadcn/ui for beautiful UI components
