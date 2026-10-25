import WorkflowHistory from '@/components/agents/WorkflowHistory';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card,CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { useToast } from '@/hooks/use-toast';
import api from '@/lib/api';
import { API_BASE_URL } from '@/lib/config';
import { useAuthStore } from '@/store/authStore';
import { AnimatePresence,motion } from 'framer-motion';
import {
AlertCircle,
ArrowLeft,
BarChart3,
Bot,
CheckCircle2,
Clock,
Loader2,
Mail,
Mic,
PenTool,
Search,
Send,
Sparkles,
Target,
Zap
} from 'lucide-react';
import { useEffect,useRef,useState } from 'react';
import { useNavigate } from 'react-router-dom';

const AGENTS = [
  { id: 'ORCHESTRATOR', name: 'Orchestrator', emoji: '🎯', color: 'from-slate-600 to-slate-800', icon: Target },
  { id: 'SCOUT', name: 'Scout', emoji: '🔍', color: 'from-sky-500 to-blue-600', icon: Search },
  { id: 'GHOSTWRITER', name: 'Ghostwriter', emoji: '✍️', color: 'from-violet-500 to-purple-600', icon: PenTool },
  { id: 'COACH', name: 'Coach', emoji: '🎤', color: 'from-rose-500 to-pink-600', icon: Mic },
  { id: 'OUTREACH', name: 'Outreach', emoji: '📧', color: 'from-amber-500 to-orange-600', icon: Mail },
  { id: 'ANALYST', name: 'Analyst', emoji: '📊', color: 'from-emerald-500 to-teal-600', icon: BarChart3 },
];

const WORKFLOWS = [
  { type: 'AUTO_PILOT', label: 'Auto-Pilot', desc: 'Find → Review → Prepare', icon: Zap, color: 'border-indigo-200 bg-indigo-50 text-indigo-700' },
  { type: 'INTERVIEW_BLITZ', label: 'Interview Blitz', desc: 'Research → Prep → Practice', icon: Mic, color: 'border-rose-200 bg-rose-50 text-rose-700' },
  { type: 'STEALTH_NETWORKING', label: 'Stealth Networking', desc: 'Plan → Draft → Review', icon: Mail, color: 'border-amber-200 bg-amber-50 text-amber-700' },
];

interface OrchestrationResult {
  workflowId: string;
  workflowType: string;
  status: string;
  thoughts: string[];
  finalReport?: string;
  userGoal?: string;
  retryable?: boolean;
  error?: string;
  steps?: { stepId: string; action: string; status: string; attempts: number; error?: string; model?: string }[];
  durationMs: number;
  artifacts?: Record<string, any>;
}

export default function AgentCommandCenter() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const thoughtsEndRef = useRef<HTMLDivElement>(null);

  const [goal, setGoal] = useState('');
  const [targetCompany, setTargetCompany] = useState('');
  const [targetRole, setTargetRole] = useState('');
  const [selectedWorkflow, setSelectedWorkflow] = useState<string | null>(null);
  const [isExecuting, setIsExecuting] = useState(false);
  const [result, setResult] = useState<OrchestrationResult | null>(null);
  const [visibleThoughts, setVisibleThoughts] = useState<string[]>([]);
  const [useStreaming, setUseStreaming] = useState(true);
  const [historyRefresh, setHistoryRefresh] = useState(0);
  const startRequest = useRef<{ body: string; id: string } | null>(null);
  const getStartRequest = () => {
    const body = JSON.stringify({ goal, workflowType: selectedWorkflow || undefined,
      targetCompany: targetCompany || undefined, targetRole: targetRole || undefined });
    if (!startRequest.current || startRequest.current.body !== body)
      startRequest.current = { body, id: crypto.randomUUID() };
    return { ...JSON.parse(body), requestId: startRequest.current.id };
  };
  const openSaved = async (id: string) => {
    try {
      const response = await api.get(`/api/v1/agents/workflows/${id}`);
      const saved: OrchestrationResult = response.data.data;
      setResult(saved);
      setVisibleThoughts(saved.thoughts);
    } catch {
      toast({ title: 'Unable to open workflow', description: 'Please refresh your saved workflow history.', variant: 'destructive' });
    }
  };
  const viewedRunId = result?.workflowId;
  const viewedStatus = result?.status;
  useEffect(() => {
    if (isExecuting || !viewedRunId || !viewedStatus || !['RUNNING', 'PENDING'].includes(viewedStatus)) return;
    let active = true;
    const id = viewedRunId;
    const timer = window.setInterval(() => {
      api.get(`/api/v1/agents/workflows/${id}`).then(response => {
        if (active) {
          setResult(response.data.data);
          setVisibleThoughts(response.data.data.thoughts);
        }
      }).catch(() => {});
    }, 5000);
    return () => { active = false; window.clearInterval(timer); };
  }, [viewedRunId, viewedStatus, isExecuting]);

  useEffect(() => {
    thoughtsEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [visibleThoughts]);

  const executeWorkflowStreaming = async (retryId?: string) => {
    if (isExecuting) return;
    if (!retryId && !goal.trim()) {
      toast({ title: 'Goal Required', description: 'Tell the agents what you want to achieve.', variant: 'destructive' });
      return;
    }

    setIsExecuting(true);
    setResult(null);
    setVisibleThoughts([]);
    let runId = retryId;

    try {
      const token = useAuthStore.getState().accessToken;
      const baseUrl = API_BASE_URL;
      
      const url = retryId ? `/api/v1/agents/workflows/${retryId}/retry/stream` : '/api/v1/agents/orchestrate/stream';
      const response = await fetch(`${baseUrl}${url}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: retryId ? undefined : JSON.stringify(getStartRequest()),
      });

      if (!response.ok) {
        throw new Error('Failed to start workflow');
      }

      const reader = response.body?.getReader();
      const decoder = new TextDecoder();

      if (!reader) {
        throw new Error('No response body');
      }

      let buffer = '';
      let receivedTerminalEvent = false;

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.slice(5).trimStart();
            if (data === '[DONE]') continue;

            try {
              const event = JSON.parse(data);
              
              if (event.type === 'workflow_start') {
                runId = event.workflowId;
                startRequest.current = null;
                setHistoryRefresh(value => value + 1);
                setVisibleThoughts(prev => [...prev, `[ORCHESTRATOR] Starting workflow: ${event.workflowType}`]);
              } else if (event.type === 'thought') {
                setVisibleThoughts(prev => [...prev, `[${event.agent}] ${event.thought}`]);
              } else if (event.type === 'workflow_complete') {
                receivedTerminalEvent = true;
                setResult(event);
                setVisibleThoughts(event.thoughts || []);
                startRequest.current = null;
                toast(event.status === 'COMPLETED'
                  ? { title: 'Workflow Complete', description: `Finished in ${(event.durationMs / 1000).toFixed(1)}s` }
                  : ['RUNNING', 'PENDING'].includes(event.status)
                    ? { title: 'Workflow in progress', description: 'Progress is saved. Open history to check the latest steps.' }
                    : { title: 'Workflow stopped', description: 'Open saved workflow history to resume unfinished steps.', variant: 'destructive' });
              } else if (event.type === 'error') {
                toast({ title: 'Check saved workflow', description: event.message, variant: 'destructive' });
                if (event.workflowId) await openSaved(event.workflowId);
                return;
              }
            } catch (e) {
              console.error('Failed to parse SSE event:', e);
            }
          }
        }
      }
      if (!receivedTerminalEvent) throw new Error('Workflow stream ended before a result was received');
    } catch (err) {
      console.error('Streaming error:', err);
      toast({ title: 'Live connection interrupted', description: 'Your saved workflow may still be running. Open its history to check progress.', variant: 'destructive' });
      if (runId) await openSaved(runId);
    } finally {
      setIsExecuting(false);
      setHistoryRefresh(value => value + 1);
    }
  };

  const executeWorkflowSync = async () => {
    if (isExecuting) return;
    if (!goal.trim()) {
      toast({ title: 'Goal Required', description: 'Tell the agents what you want to achieve.', variant: 'destructive' });
      return;
    }

    setIsExecuting(true);
    setResult(null);
    setVisibleThoughts([]);

    try {
      const response = await api.post('/api/v1/agents/orchestrate', getStartRequest());
      startRequest.current = null;

      const data: OrchestrationResult = response.data.data;
      setResult(data);

      // Animate thoughts appearing one by one
      if (data.thoughts) {
        for (let i = 0; i < data.thoughts.length; i++) {
          await new Promise(r => setTimeout(r, 300));
          setVisibleThoughts(prev => [...prev, data.thoughts[i]]);
        }
      }

      toast(data.status === 'COMPLETED'
        ? { title: 'Workflow Complete', description: `Finished in ${(data.durationMs / 1000).toFixed(1)}s` }
        : ['RUNNING', 'PENDING'].includes(data.status)
          ? { title: 'Workflow in progress', description: 'Progress is saved. Open history to check the latest steps.' }
          : { title: 'Workflow stopped', description: 'Open saved workflow history to resume unfinished steps.', variant: 'destructive' });
    } catch (err) {
      toast({ title: 'Check saved workflow', description: 'The request did not return a result. Open history to check whether work is still running.', variant: 'destructive' });
    } finally {
      setIsExecuting(false);
      setHistoryRefresh(value => value + 1);
    }
  };

  const executeWorkflow = () => {
    if (useStreaming) {
      executeWorkflowStreaming();
    } else {
      executeWorkflowSync();
    }
  };

  const getAgentFromThought = (thought: string) => {
    // Parse agent name from format like "[ORCHESTRATOR] Starting workflow"
    const match = thought.match(/\[(\w+)\]/);
    if (match) {
      const agentName = match[1];
      return AGENTS.find(a => a.id === agentName) || AGENTS[0];
    }
    return AGENTS.find(a => thought.includes(a.emoji) || thought.includes(a.name)) || AGENTS[0];
  };

  return (
    <div className="min-h-screen bg-slate-50/50 pb-20">
      {/* Header */}
      <div className="bg-white border-b sticky top-0 z-20">
        <div className="container mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Button variant="ghost" size="icon" onClick={() => navigate('/dashboard')}>
              <ArrowLeft className="h-5 w-5" />
            </Button>
            <div>
              <h1 className="text-xl font-black text-slate-900 flex items-center gap-2">
                <Bot className="h-5 w-5 text-indigo-600" /> Agent Command Center
              </h1>
              <p className="text-xs text-slate-400 font-medium">6 specialized AI agents at your service</p>
            </div>
          </div>
          <Badge variant="outline" className="rounded-full px-4 py-1 font-black text-[10px] tracking-widest border-indigo-200 text-indigo-600">
            MULTI-AGENT v1.0
          </Badge>
        </div>
      </div>

      <div className="container mx-auto px-4 py-8 max-w-7xl">
        {/* Agent Roster Bar */}
        <div className="flex gap-3 mb-8 overflow-x-auto pb-2">
          {AGENTS.map(agent => (
            <div
              key={agent.id}
              className="flex items-center gap-2.5 px-5 py-3 rounded-2xl bg-white border border-slate-100 shadow-sm hover:shadow-md transition-all shrink-0 group"
            >
              <div className={`h-9 w-9 rounded-xl bg-gradient-to-br ${agent.color} flex items-center justify-center text-white text-sm shadow-sm group-hover:scale-110 transition-transform`}>
                {agent.emoji}
              </div>
              <div>
                <p className="text-sm font-bold text-slate-800">{agent.name}</p>
                <p className="text-[10px] text-slate-400 font-medium uppercase tracking-wider">{agent.id}</p>
              </div>
            </div>
          ))}
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          {/* Left Panel — Mission Control */}
          <div className="lg:col-span-4 space-y-6">
            <Card className="border-slate-200 shadow-sm rounded-3xl overflow-hidden">
              <CardContent className="p-6 space-y-5">
                <div>
                  <h3 className="text-sm font-black text-slate-400 uppercase tracking-widest mb-4">Mission Briefing</h3>
                  <Input
                    placeholder="e.g. Find me a PM role at Stripe and prepare my application"
                    value={goal}
                    onChange={(e) => setGoal(e.target.value)}
                    className="h-12 text-sm font-medium rounded-xl"
                    onKeyDown={(e) => e.key === 'Enter' && executeWorkflow()}
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Target Company</label>
                    <Input
                      placeholder="Google, Stripe..."
                      value={targetCompany}
                      onChange={(e) => setTargetCompany(e.target.value)}
                      className="mt-1 h-10 text-sm rounded-xl"
                    />
                  </div>
                  <div>
                    <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Target Role</label>
                    <Input
                      placeholder="Senior Engineer..."
                      value={targetRole}
                      onChange={(e) => setTargetRole(e.target.value)}
                      className="mt-1 h-10 text-sm rounded-xl"
                    />
                  </div>
                </div>

                <div>
                  <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest mb-2 block">Workflow Override (Optional)</label>
                  <div className="space-y-2">
                    {WORKFLOWS.map(wf => (
                      <div
                        key={wf.type}
                        onClick={() => setSelectedWorkflow(selectedWorkflow === wf.type ? null : wf.type)}
                        className={`flex items-center gap-3 p-3 rounded-xl border-2 cursor-pointer transition-all ${
                          selectedWorkflow === wf.type
                            ? wf.color + ' border-current shadow-sm'
                            : 'border-transparent bg-slate-50 hover:bg-slate-100'
                        }`}
                      >
                        <wf.icon className="h-4 w-4 shrink-0" />
                        <div>
                          <p className="text-sm font-bold">{wf.label}</p>
                          <p className="text-[10px] opacity-70">{wf.desc}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="flex items-center justify-between p-3 bg-slate-50 rounded-xl">
                  <div>
                    <p className="text-sm font-bold text-slate-700">Real-time Streaming</p>
                    <p className="text-[10px] text-slate-400">See agent thoughts as they happen</p>
                  </div>
                  <button
                    onClick={() => setUseStreaming(!useStreaming)}
                    className={`relative w-12 h-6 rounded-full transition-colors ${
                      useStreaming ? 'bg-indigo-600' : 'bg-slate-300'
                    }`}
                  >
                    <div
                      className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-transform ${
                        useStreaming ? 'left-7' : 'left-1'
                      }`}
                    />
                  </button>
                </div>

                <Button
                  onClick={executeWorkflow}
                  disabled={isExecuting || !goal.trim()}
                  className="w-full h-14 rounded-2xl bg-gradient-to-r from-indigo-600 to-violet-600 hover:from-indigo-700 hover:to-violet-700 text-white font-bold text-base shadow-lg shadow-indigo-200 transition-all"
                >
                  {isExecuting ? (
                    <>
                      <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Agents Working...
                    </>
                  ) : (
                    <>
                      <Send className="mr-2 h-5 w-5" /> Deploy Agents
                    </>
                  )}
                </Button>
              </CardContent>
            </Card>
            <WorkflowHistory refreshKey={historyRefresh} disabled={isExecuting} onOpen={openSaved} />
          </div>

          {/* Right Panel — Agent Activity Feed */}
          <div className="lg:col-span-8 space-y-6">
            {!result && !isExecuting ? (
              <div className="h-full min-h-[500px] flex flex-col items-center justify-center bg-white rounded-3xl border-2 border-dashed border-slate-200 p-12 text-center">
                <div className="relative mb-8">
                  <div className="h-24 w-24 rounded-3xl bg-gradient-to-br from-indigo-500 to-violet-600 flex items-center justify-center shadow-2xl shadow-indigo-200">
                    <Sparkles className="h-12 w-12 text-white" />
                  </div>
                  <div className="absolute -top-2 -right-2 h-8 w-8 rounded-full bg-emerald-500 flex items-center justify-center border-4 border-white">
                    <span className="text-white text-xs font-bold">6</span>
                  </div>
                </div>
                <h2 className="text-2xl font-black text-slate-900 mb-2">Your AI Agent Squad is Standing By</h2>
                <p className="text-slate-500 max-w-md leading-relaxed">
                  Describe your career goal in natural language. The Orchestrator will assemble the right
                  team of agents and execute a multi-step strategy.
                </p>
                <div className="mt-8 flex flex-wrap justify-center gap-2">
                  {['Find me a job at FAANG', 'Prepare for tomorrow\'s interview', 'Connect with Stripe\'s PM team'].map(eg => (
                    <button
                      key={eg}
                      onClick={() => setGoal(eg)}
                      className="px-4 py-2 rounded-full bg-slate-100 text-sm font-medium text-slate-600 hover:bg-indigo-50 hover:text-indigo-600 transition-colors"
                    >
                      "{eg}"
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <div className="space-y-6">
                {/* Status Bar */}
                <div className="flex items-center justify-between px-4">
                  <div className="flex items-center gap-3">
                    {isExecuting ? (
                      <div className="flex items-center gap-2 px-4 py-2 bg-amber-50 text-amber-700 rounded-full border border-amber-200 font-bold text-sm animate-pulse">
                        <Loader2 className="h-4 w-4 animate-spin" /> Agents Executing...
                      </div>
                    ) : result?.status === 'COMPLETED' ? (
                      <div className="flex items-center gap-2 px-4 py-2 bg-emerald-50 text-emerald-700 rounded-full border border-emerald-200 font-bold text-sm">
                        <CheckCircle2 className="h-4 w-4" /> Workflow Complete
                      </div>
                    ) : result && ['RUNNING', 'PENDING'].includes(result.status) ? (
                      <div className="flex items-center gap-2 px-4 py-2 bg-amber-50 text-amber-700 rounded-full font-bold text-sm">
                        <Clock className="h-4 w-4" /> Workflow in progress
                      </div>
                    ) : (
                      <div className="flex items-center gap-2 px-4 py-2 bg-red-50 text-red-700 rounded-full border border-red-200 font-bold text-sm">
                        <AlertCircle className="h-4 w-4" /> Workflow {result?.status === 'INTERRUPTED' ? 'interrupted' : 'stopped'}
                      </div>
                    )}
                    {result && (
                      <Badge variant="outline" className="font-mono text-xs">{result.workflowType}</Badge>
                    )}
                  </div>
                  {result && (
                    <div className="flex items-center gap-1.5 text-sm text-slate-400 font-medium">
                      <Clock className="h-4 w-4" /> {(result.durationMs / 1000).toFixed(1)}s
                    </div>
                  )}
                </div>

                {/* Agent Thought Stream */}
                <Card className="rounded-3xl border-slate-200 shadow-sm overflow-hidden">
                  <div className="bg-slate-900 px-6 py-3 flex items-center gap-2">
                    <div className="h-3 w-3 rounded-full bg-red-500" />
                    <div className="h-3 w-3 rounded-full bg-amber-500" />
                    <div className="h-3 w-3 rounded-full bg-emerald-500" />
                    <span className="ml-4 text-xs text-slate-400 font-mono">agent-stream.log</span>
                  </div>
                  <CardContent className="p-0">
                    <div className="max-h-[400px] overflow-y-auto p-6 space-y-3 bg-slate-950">
                      <AnimatePresence>
                        {visibleThoughts.map((thought, i) => {
                          const agent = getAgentFromThought(thought);
                          return (
                            <motion.div
                              key={i}
                              initial={{ opacity: 0, x: -20 }}
                              animate={{ opacity: 1, x: 0 }}
                              transition={{ duration: 0.3 }}
                              className="flex items-start gap-3"
                            >
                              <div className={`h-7 w-7 rounded-lg bg-gradient-to-br ${agent.color} flex items-center justify-center text-xs shadow-sm shrink-0 mt-0.5`}>
                                {agent.emoji}
                              </div>
                              <div className="flex-1 min-w-0">
                                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">{agent.name}</span>
                                <p className="text-sm text-slate-300 font-mono leading-relaxed break-words">
                                  {thought.replace(/\[.*?\]\s*/, '')}
                                </p>
                              </div>
                            </motion.div>
                          );
                        })}
                      </AnimatePresence>
                      {isExecuting && (
                        <motion.div
                          initial={{ opacity: 0 }}
                          animate={{ opacity: 1 }}
                          className="flex items-center gap-2 text-indigo-400 text-sm font-mono"
                        >
                          <Loader2 className="h-4 w-4 animate-spin" />
                          <span className="animate-pulse">Processing...</span>
                        </motion.div>
                      )}
                      <div ref={thoughtsEndRef} />
                    </div>
                  </CardContent>
                </Card>

                {result && !isExecuting && (
                  <Card className="rounded-3xl border-slate-200">
                    <CardContent className="p-6 space-y-4">
                      <h3 className="font-bold text-slate-800">{result.userGoal || 'Saved workflow'}</h3>
                      <p className="text-sm text-slate-500">Status: {result.status}</p>
                      {result.error && <p role="alert" className="text-sm text-red-600">{result.error}</p>}
                      <ol className="space-y-2">
                        {result.steps?.map(step => (
                          <li key={step.stepId} className="rounded-xl bg-slate-50 p-3 text-sm">
                            <span className="font-medium">{step.action}</span> — {step.status}
                            <span className="text-slate-500"> · Attempts: {step.attempts}</span>
                            {step.error && <p className="mt-1 text-red-600">{step.error}</p>}
                          </li>
                        ))}
                      </ol>
                      {result.retryable && <div className="space-y-2">
                        <p className="text-xs text-slate-500">Saved steps and model responses are reused. Calls with an unknown outcome need support reconciliation before retry; a later provider attempt can incur usage.</p>
                        <Button onClick={() => executeWorkflowStreaming(result.workflowId)}>Resume unfinished steps</Button>
                      </div>}
                    </CardContent>
                  </Card>
                )}

                {/* Final Report */}
                {result && !isExecuting && (
                  <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.5 }}>
                    <Card className="rounded-3xl border-slate-200 shadow-sm">
                      <CardContent className="p-8">
                        <div className="flex items-center gap-3 mb-6">
                          <div className="h-10 w-10 rounded-2xl bg-gradient-to-br from-indigo-500 to-violet-600 flex items-center justify-center">
                            <Sparkles className="h-5 w-5 text-white" />
                          </div>
                          <div>
                            <h3 className="text-lg font-black text-slate-900">{result.status === 'COMPLETED' ? 'Executive Summary' : 'Saved outputs'}</h3>
                            <p className="text-xs text-slate-400 font-medium">Compiled by the Orchestrator Agent</p>
                          </div>
                        </div>
                        <div className="prose prose-slate max-w-none text-slate-600 leading-relaxed whitespace-pre-wrap mb-8">
                          {result.finalReport || 'Completed outputs are saved below for review. Check the step list for current progress.'}
                        </div>
                        
                        {/* Agent Action Outputs (Artifacts) */}
                        {result.artifacts && Object.keys(result.artifacts).length > 0 && (
                          <div className="mt-8 pt-8 border-t border-slate-100">
                            <h4 className="text-sm font-black text-slate-900 uppercase tracking-widest mb-4 flex items-center gap-2">
                              <Zap className="h-4 w-4 text-indigo-500" /> Generated Artifacts & Actions
                            </h4>
                            <div className="space-y-4 mb-4">
                              {[
                                ['resumeSuggestions', 'Resume suggestions'],
                                ['questions', 'Practice questions'],
                                ['thankYouDraft', 'Thank-you email draft'],
                                ['emailDraft', 'Outreach draft'],
                                ['followUpDraft', 'Follow-up draft'],
                                ['companyInsights', 'Company background'],
                                ['salaryAdvice', 'Compensation guidance'],
                                ['cultureAnalysis', 'Fit questions'],
                              ].map(([key, label]) => typeof result.artifacts?.[key] === 'string' && (
                                <div key={key}>
                                  <h5 className="font-semibold text-slate-800">{label}</h5>
                                  <p className="whitespace-pre-wrap text-sm text-slate-600">{result.artifacts[key]}</p>
                                </div>
                              ))}
                            </div>
                            <div className="flex flex-wrap gap-3">
                              {result.artifacts.scoutedJobs && (
                                <Button onClick={() => navigate('/jobs')} variant="outline" className="rounded-xl border-indigo-200 text-indigo-700 bg-indigo-50 hover:bg-indigo-100 font-bold">
                                  <Search className="h-4 w-4 mr-2" /> View {Array.isArray(result.artifacts.scoutedJobs) ? result.artifacts.scoutedJobs.length : ''} Scouted Jobs
                                </Button>
                              )}
                              {result.artifacts.tailoredResumeId && (
                                <Button onClick={() => navigate('/resumes')} variant="outline" className="rounded-xl border-violet-200 text-violet-700 bg-violet-50 hover:bg-violet-100 font-bold">
                                  <PenTool className="h-4 w-4 mr-2" /> View Tailored Resume
                                </Button>
                              )}
                              {result.artifacts.practiceUrl && (
                                <Button onClick={() => navigate('/ai/interview-prep')} variant="outline" className="rounded-xl border-rose-200 text-rose-700 bg-rose-50 hover:bg-rose-100 font-bold">
                                  <Mic className="h-4 w-4 mr-2" /> Start Practice Session
                                </Button>
                              )}
                              {result.artifacts.savedLeadId && (
                                <Button onClick={() => navigate('/networking')} variant="outline" className="rounded-xl border-amber-200 text-amber-700 bg-amber-50 hover:bg-amber-100 font-bold">
                                  <Mail className="h-4 w-4 mr-2" /> View Networking CRM Lead
                                </Button>
                              )}
                            </div>
                          </div>
                        )}
                      </CardContent>
                    </Card>
                  </motion.div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
