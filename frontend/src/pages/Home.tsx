import { Button } from '@/components/ui/button';
import { Tabs,TabsContent,TabsList,TabsTrigger } from "@/components/ui/tabs";
import {
ArrowRight,
Bot,
Brain,
Briefcase,
Check,
FileEdit,
FileText,
Globe,
Layout,
Rocket,
Search,
Sparkles,
TrendingUp,
Wand2,
Zap
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';

export default function Home() {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 selection:bg-indigo-100">
      {/* 1. NAVIGATION BAR */}
      <nav className="border-b border-slate-200 bg-white/80 backdrop-blur-xl sticky top-0 z-50">
        <div className="container mx-auto px-4 h-20 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="h-10 w-10 bg-indigo-600 rounded-xl flex items-center justify-center shadow-lg shadow-indigo-200">
              <Rocket className="text-white h-6 w-6" />
            </div>
            <span className="text-xl font-black tracking-tighter uppercase flex items-center gap-2 text-slate-900">
              Job Pilot  <Bot className="h-4 w-4 text-indigo-600" />
            </span>
          </div>
          <div className="hidden lg:flex items-center gap-8 text-sm font-semibold text-slate-500">
            <a href="#features" className="hover:text-indigo-600 transition-colors">Features</a>
            <a href="#how-it-works" className="hover:text-indigo-600 transition-colors">How It Works</a>
            <a href="#pricing" className="hover:text-indigo-600 transition-colors">Pricing</a>
          </div>
          <div className="flex items-center gap-4">
            <Button variant="ghost" className="text-slate-600 hidden sm:flex font-semibold" onClick={() => navigate('/login')}>Login</Button>
            <Button onClick={() => navigate('/register')} className="bg-indigo-600 text-white hover:bg-indigo-700 font-bold px-6 rounded-full shadow-lg shadow-indigo-200">
              Get Started – Free
            </Button>
          </div>
        </div>
      </nav>

      {/* 2. HERO SECTION */}
      <header className="relative pt-24 pb-32 overflow-hidden bg-white">
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full h-full bg-[radial-gradient(circle_at_center,_var(--tw-gradient-stops))] from-indigo-50 via-transparent to-transparent blur-3xl opacity-60" />

        <div className="container mx-auto px-4 relative z-10 text-center">
          <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-indigo-50 border border-indigo-100 text-indigo-600 text-xs font-bold uppercase tracking-widest mb-8">
            <Zap className="h-3 w-3 fill-current" /> Prepare Your Next Application with AI
          </div>

          <h1 className="text-6xl md:text-8xl font-black tracking-tight mb-8 text-slate-900 leading-[1.1]">
            Prepare Your Next <br className="hidden md:block" /> Application with AI.
          </h1>

          <p className="text-slate-600 text-lg md:text-xl max-w-2xl mx-auto mb-12 leading-relaxed font-medium">
            Resume optimization, cover letters, interview practice, and application tracking – all powered by advanced AI career agents.
          </p>

          <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
            <Button size="lg" className="h-16 px-10 text-lg font-bold rounded-2xl gap-2 w-full sm:w-auto bg-indigo-600 text-white hover:bg-indigo-700 shadow-xl shadow-indigo-100" onClick={() => navigate('/register')}>
              Start Free <ArrowRight className="h-5 w-5" />
            </Button>
            <Button asChild size="lg" variant="outline" className="h-16 px-10 text-lg font-bold rounded-2xl border-slate-200 bg-white hover:bg-slate-50 w-full sm:w-auto gap-2 shadow-sm">
              <a href="#how-it-works">See How It Works</a>
            </Button>
          </div>
        </div>
      </header>

      {/* 4. CORE FEATURES SECTION */}
      <section id="features" className="py-32">
        <div className="container mx-auto px-4">
          <div className="text-center mb-20">
            <h2 className="text-3xl md:text-5xl font-black mb-4 tracking-tighter text-slate-900">Powered by Intelligence</h2>
            <p className="text-slate-500 max-w-xl mx-auto font-semibold italic">Tools designed to give you an unfair advantage.</p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            <FeatureCard
              icon={<FileText />}
              title="Resume Intelligence"
              description="Upload a text-based PDF or DOCX and review AI suggestions for your resume."
              accent="indigo"
            />
            <FeatureCard
              icon={<FileEdit />}
              title="AI Optimizer"
              description="Generate job-specific wording suggestions and check every edit against your experience."
              accent="indigo"
            />
            <FeatureCard
              icon={<Sparkles />}
              title="Cover Letter Gen"
              description="Personalized, tone-controllable cover letters in seconds that sound like you."
              accent="indigo"
            />
            <FeatureCard
              icon={<Layout />}
              title="Application Tracker"
              description="Kanban board to manage applications: Applied → Interview → Offer."
              accent="indigo"
            />
            <FeatureCard
              icon={<Brain />}
              title="Interview Simulator"
              description="Mock interviews with Hiring Manager, Tech Lead, and HR agents."
              accent="indigo"
            />
            <FeatureCard
              icon={<Search />}
              title="Smart Researcher"
              description="Agentic search: finds jobs from internal DB + web; ranks them by match score."
              accent="indigo"
            />
          </div>
        </div>
      </section>

      {/* 5. HOW IT WORKS */}
      <section id="how-it-works" className="py-32 bg-slate-50">
        <div className="container mx-auto px-4">
          <div className="text-center mb-20">
            <h2 className="text-3xl md:text-5xl font-black mb-4 tracking-tighter text-slate-900">How It Works</h2>
            <p className="text-slate-500 font-medium italic">Three simple steps to your next career breakthrough.</p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-12">
            <StepCard
              step="1"
              icon={<FileText className="h-10 w-10" />}
              title="Upload & Paste"
              description="Upload your resume and paste the link to any job description."
            />
            <StepCard
              step="2"
              icon={<Wand2 className="h-10 w-10" />}
              title="AI Generation"
              description="Our agents optimize your bullet points and generate tailored materials."
            />
            <StepCard
              step="3"
              icon={<TrendingUp className="h-10 w-10" />}
              title="Apply & Track"
              description="Submit your optimized materials and track status to the finish line."
            />
          </div>
        </div>
      </section>

      {/* 6. ADVANCED FEATURES SHOWCASE */}
      <section className="py-32 bg-white overflow-hidden">
        <div className="container mx-auto px-4">
          <Tabs defaultValue="memory" className="w-full max-w-5xl mx-auto">
            <div className="flex flex-col md:flex-row items-center justify-between mb-12 gap-8">
              <div>
                <h2 className="text-4xl font-black tracking-tighter text-slate-900 uppercase">Advanced Agents</h2>
                <p className="text-slate-500 mt-2 font-medium">Next-level automation for senior professionals.</p>
              </div>
              <TabsList className="bg-slate-100 border border-slate-200 p-1 rounded-2xl h-14">
                <TabsTrigger value="memory" className="rounded-xl px-6 data-[state=active]:bg-indigo-600 data-[state=active]:text-white font-bold transition-all">Memory</TabsTrigger>
                <TabsTrigger value="rag" className="rounded-xl px-6 data-[state=active]:bg-indigo-600 data-[state=active]:text-white font-bold transition-all">RAG</TabsTrigger>
                <TabsTrigger value="interview" className="rounded-xl px-6 data-[state=active]:bg-indigo-600 data-[state=active]:text-white font-bold transition-all">Interview</TabsTrigger>
                <TabsTrigger value="auto" className="rounded-xl px-6 data-[state=active]:bg-indigo-600 data-[state=active]:text-white font-bold transition-all">Save Jobs</TabsTrigger>
              </TabsList>
            </div>

            <TabsContent value="memory" className="animate-in fade-in slide-in-from-bottom-8 duration-700">
              <AdvancedFeaturePanel
                title="Episodic Memory"
                description="Resume optimization can use recent interactions for the selected job as context."
                features={["Job-specific context", "Recent interactions", "Saved conversation history"]}
              />
            </TabsContent>
            <TabsContent value="rag" className="animate-in fade-in slide-in-from-bottom-8 duration-700">
              <AdvancedFeaturePanel
                title="Agentic RAG"
                description="Uses saved jobs and, when available, web search to suggest opportunities for your review."
                features={["Saved-job search", "Semantic matching", "External search when available"]}
              />
            </TabsContent>
            <TabsContent value="interview" className="animate-in fade-in slide-in-from-bottom-8 duration-700">
              <AdvancedFeaturePanel
                title="Multi-Agent Interview"
                description="Practice with a full panel of AI interviewers. Rotate between Tech, HR, and Leadership personas."
                features={["Persona-driven logic", "Turn-taking simulations", "Persona scores"]}
              />
            </TabsContent>
            <TabsContent value="auto" className="animate-in fade-in slide-in-from-bottom-8 duration-700">
              <AdvancedFeaturePanel
                title="Save Jobs for Review"
                description="Save selected opportunities to your tracker, then apply directly on the employer’s site."
                features={["Saved opportunities", "Manual status updates", "Application notes"]}
              />
            </TabsContent>
          </Tabs>
        </div>
      </section>

      <section id="pricing" className="py-20 bg-slate-50">
        <div className="container mx-auto px-4 text-center space-y-4">
          <h2 className="text-3xl font-bold">Try Job Pilot</h2>
          <p className="text-slate-600">New accounts receive 5 introductory AI credits. Paid upgrades are not available yet.</p>
        </div>
      </section>

      {/* 11. FOOTER */}
      <footer className="py-20 border-t border-slate-100 bg-slate-50">
        <div className="container mx-auto px-4">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-12 mb-20 text-center md:text-left">
            <div className="space-y-6">
              <div className="flex items-center gap-2 justify-center md:justify-start">
                <Rocket className="h-6 w-6 text-indigo-600" />
                <span className="text-lg font-black uppercase tracking-tighter text-slate-900">Job Pilot </span>
              </div>
              <p className="text-slate-500 text-sm leading-relaxed font-medium">
                An AI-assisted workspace for preparing applications and organizing your job search.
              </p>
            </div>
            <div>
              <h4 className="font-bold text-xs uppercase tracking-widest mb-6 text-slate-900">Platform</h4>
              <ul className="space-y-4 text-sm text-slate-500 font-bold uppercase tracking-tighter">
                <li><a href="#" className="hover:text-indigo-600 transition-colors">Features</a></li>
                <li><a href="#" className="hover:text-indigo-600 transition-colors">Pricing</a></li>
                <li><a href="#" className="hover:text-indigo-600 transition-colors">Roadmap</a></li>
              </ul>
            </div>
            <div>
              <h4 className="font-bold text-xs uppercase tracking-widest mb-6 text-slate-900">Company</h4>
              <ul className="space-y-4 text-sm text-slate-500 font-bold uppercase tracking-tighter">
                <li><a href="#" className="hover:text-indigo-600 transition-colors">About Us</a></li>
                <li><a href="#" className="hover:text-indigo-600 transition-colors">Privacy</a></li>
                <li><a href="#" className="hover:text-indigo-600 transition-colors">Terms</a></li>
              </ul>
            </div>
            <div className="space-y-6">
              <h4 className="font-bold text-xs uppercase tracking-widest mb-6 text-slate-900">Connect</h4>
              <div className="flex gap-4 justify-center md:justify-start text-slate-400">
                <Globe className="h-5 w-5 hover:text-indigo-600 cursor-pointer transition-colors" />
                <Briefcase className="h-5 w-5 hover:text-indigo-600 cursor-pointer transition-colors" />
                <Zap className="h-5 w-5 hover:text-indigo-600 cursor-pointer transition-colors" />
              </div>
            </div>
          </div>
          <div className="pt-10 border-t border-slate-200 text-center text-[10px] text-slate-400 font-black uppercase tracking-[0.2em]">
            © 2026 Job Pilot  AI. Built for the future of work.
          </div>
        </div>
      </footer>
    </div>
  );
}

function FeatureCard({ icon, title, description }: any) {
  return (
    <div className={`group p-8 rounded-3xl bg-white border border-slate-100 transition-all duration-500 hover:-translate-y-1 hover:shadow-2xl hover:shadow-indigo-500/10 hover:border-indigo-100`}>
      <div className={`h-14 w-14 rounded-2xl bg-indigo-50 flex items-center justify-center mb-6 transition-all duration-500 group-hover:scale-110 text-indigo-600`}>
        {icon}
      </div>
      <h3 className="text-xl font-bold mb-3 text-slate-900 group-hover:text-indigo-600 transition-colors">{title}</h3>
      <p className="text-slate-500 text-sm leading-relaxed font-medium">{description}</p>
    </div>
  );
}

function StepCard({ step, icon, title, description }: any) {
  return (
    <div className="text-center space-y-6 relative group">
      <div className="h-20 w-20 bg-white border border-slate-200 rounded-full flex items-center justify-center mx-auto relative z-10 group-hover:border-indigo-600 transition-all duration-500 group-hover:shadow-xl group-hover:shadow-indigo-100">
        <div className="absolute -top-2 -right-2 h-8 w-8 bg-indigo-600 rounded-full flex items-center justify-center text-xs font-black text-white">
          {step}
        </div>
        <div className="text-slate-700 group-hover:text-indigo-600 transition-colors">{icon}</div>
      </div>
      <h3 className="text-xl font-bold text-slate-900">{title}</h3>
      <p className="text-slate-500 text-sm leading-relaxed px-4 font-medium italic">{description}</p>
    </div>
  );
}

function AdvancedFeaturePanel({ title, description, features }: any) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-12 bg-slate-50 border border-slate-100 rounded-[40px] p-8 md:p-16 items-center">
      <div className="space-y-8">
        <h3 className="text-4xl font-black uppercase tracking-tighter text-slate-900">{title}</h3>
        <p className="text-slate-600 text-lg leading-relaxed font-medium italic">{description}</p>
        <div className="space-y-4">
          {features.map((f: string, i: number) => (
            <div key={i} className="flex items-center gap-3 text-sm font-bold text-slate-700">
              <Check className="h-5 w-5 text-indigo-600" /> {f}
            </div>
          ))}
        </div>
      </div>
      <div className="aspect-[4/3] bg-white rounded-3xl border border-slate-200 flex items-center justify-center relative overflow-hidden group shadow-inner">
        <div className="absolute inset-0 bg-indigo-600/5 opacity-0 group-hover:opacity-100 transition-opacity" />
        <Layout className="h-20 w-20 text-slate-100 group-hover:text-indigo-100 transition-colors" />
        <div className="absolute bottom-8 left-8 right-8 h-12 bg-slate-50 border border-slate-100 rounded-xl animate-pulse" />
      </div>
    </div>
  );
}
