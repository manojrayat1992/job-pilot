# Job Pilot  Frontend

A professional React + TypeScript frontend for the AI Job Pilot  application.

## Tech Stack

- **React 18** - UI library
- **TypeScript** - Type safety
- **Vite** - Build tool and dev server
- **TailwindCSS** - Styling
- **shadcn/ui** - UI components
- **React Router** - Client-side routing
- **Axios** - HTTP client
- **Zustand** - State management
- **React Query** - Data fetching
- **Lucide React** - Icons

## Features

- **Authentication**: Login and registration with JWT tokens
- **Dashboard**: Overview of job application statistics
- **AI Tools**:
  - Resume Optimizer - Optimize your resume for specific job descriptions
  - Cover Letter Generator - Generate personalized cover letters
  - Interview Preparation - Practice interview questions with AI
- **Job Application Tracking**: Manage and track your job applications

## Getting Started

### Prerequisites

- Node.js 18+ 
- npm or yarn

### Installation

1. Install dependencies:
```bash
npm install
```

2. Start the development server:
```bash
npm run dev
```

3. Open your browser and navigate to `http://localhost:5173`

### Build for Production

```bash
npm run build
```

The built files will be in the `dist` directory.

## Environment Variables

Create a `.env` file in the root directory:

```env
VITE_API_URL=http://localhost:8080
```

## Project Structure

```
src/
├── components/
│   └── ui/           # shadcn/ui components
├── lib/
│   ├── api.ts        # Axios configuration with interceptors
│   └── utils.ts      # Utility functions
├── pages/            # Page components
├── store/
│   └── authStore.ts  # Zustand auth store
├── types/
│   └── index.ts      # TypeScript type definitions
├── App.tsx           # Main app with routing
├── main.tsx          # Entry point
└── index.css         # Global styles
```

## API Integration

The frontend is configured to work with the backend API at `http://localhost:8080` by default. The API client includes:

- Automatic JWT token injection
- Token refresh handling
- Error handling

## Authentication Flow

1. User registers or logs in
2. JWT tokens (access + refresh) are stored in Zustand and localStorage
3. Access token is automatically included in API requests
4. Token refresh happens automatically when access token expires

## Design System

The application uses a modern, professional design with:
- Clean, minimal interface
- Consistent color scheme (blue/indigo primary)
- Responsive design for all screen sizes
- Accessible components
- Dark mode support (via Tailwind)

## License

MIT
