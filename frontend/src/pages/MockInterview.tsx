import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card,CardContent,CardHeader,CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { useToast } from '@/hooks/use-toast';
import api from '@/lib/api';
import { Resume } from '@/types';
import {
ArrowLeft,
Brain,
Building2,
Cpu,
Loader2,
Mic,
Send,
Square,
Trophy,
User,
UserCheck
} from 'lucide-react';
import { useEffect,useRef,useState } from 'react';
import { useNavigate } from 'react-router-dom';

interface Message {
  role: 'user' | 'assistant';
  persona?: 'TECH_LEAD' | 'HIRING_MANAGER' | 'HR_AGENT';
  content: string;
  timestamp: string;
}

export default function MockInterview() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const scrollRef = useRef<HTMLDivElement>(null);
  
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [selectedResumeId, setSelectedResumeId] = useState('');
  const [targetRole, setTargetRole] = useState('');
  
  const [isStarted, setIsStarted] = useState(false);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [userInput, setUserInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [currentTurn, setCurrentTurn] = useState(1);
  const [isCompleted, setIsCompleted] = useState(false);
  
  // Audio state
  const [isRecording, setIsRecording] = useState(false);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const audioPlayerRef = useRef<HTMLAudioElement>(null);

  useEffect(() => {
    fetchResumes();
  }, []);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const fetchResumes = async () => {
    try {
      const res = await api.get('/api/v1/resumes');
      setResumes(res.data.data.content);
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to fetch resumes.', variant: 'destructive' });
    }
  };

  const startInterview = async () => {
    if (!targetRole) {
      toast({ title: 'Role Required', description: 'Please specify the role you are interviewing for.', variant: 'destructive' });
      return;
    }

    setIsLoading(true);
    try {
      const res = await api.post('/api/v1/interviews/start', {
        role: targetRole,
        resumeId: selectedResumeId || null
      });
      const data = res.data.data;
      setSessionId(data.sessionId);
      setMessages([data.latestMessage]);
      setIsStarted(true);
      setCurrentTurn(data.currentTurn);
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to start interview.', variant: 'destructive' });
    } finally {
      setIsLoading(false);
    }
  };

  const handleSendMessage = async () => {
    if (!userInput.trim() || !sessionId || isLoading) return;

    const userMsg: Message = {
      role: 'user',
      content: userInput,
      timestamp: new Date().toLocaleTimeString()
    };

    setMessages(prev => [...prev, userMsg]);
    const currentInput = userInput;
    setUserInput('');
    setIsLoading(true);

    try {
      const res = await api.post(`/api/v1/interviews/${sessionId}/message`, {
        answer: currentInput
      });
      
      const data = res.data.data;
      if (data.status === 'COMPLETED') {
        setIsCompleted(true);
        toast({ title: 'Interview Complete!', description: 'Generating your final report...' });
      } else {
        setMessages(prev => [...prev, data.latestMessage]);
        setCurrentTurn(data.currentTurn);
      }
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to send message.', variant: 'destructive' });
    } finally {
      setIsLoading(false);
    }
  };

  const toggleRecording = async () => {
    if (isRecording) {
      mediaRecorderRef.current?.stop();
      setIsRecording(false);
    } else {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        const mediaRecorder = new MediaRecorder(stream);
        mediaRecorderRef.current = mediaRecorder;
        audioChunksRef.current = [];

        mediaRecorder.ondataavailable = (event) => {
          if (event.data.size > 0) {
            audioChunksRef.current.push(event.data);
          }
        };

        mediaRecorder.onstop = async () => {
          const audioBlob = new Blob(audioChunksRef.current, { type: 'audio/webm' });
          stream.getTracks().forEach(track => track.stop());
          await sendAudioMessage(audioBlob);
        };

        mediaRecorder.start();
        setIsRecording(true);
      } catch (error) {
        toast({ title: 'Microphone Error', description: 'Could not access microphone.', variant: 'destructive' });
      }
    }
  };

  const sendAudioMessage = async (audioBlob: Blob) => {
    if (!sessionId || isLoading) return;

    setIsLoading(true);
    // Optimistically show user message (we don't know text yet, so we show a placeholder)
    const tempMsg: Message = { role: 'user', content: '🎤 (Audio Message sent...)', timestamp: new Date().toLocaleTimeString() };
    setMessages(prev => [...prev, tempMsg]);

    const formData = new FormData();
    formData.append('audio', audioBlob, 'recording.webm');

    try {
      const res = await api.post(`/api/v1/interviews/${sessionId}/message/audio`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      
      const data = res.data.data;
      
      // Replace temp message with actual transcribed text if provided by backend
      if (data.transcribedText) {
        setMessages(prev => {
          const newMsgs = [...prev];
          newMsgs[newMsgs.length - 1].content = data.transcribedText;
          return newMsgs;
        });
      }

      if (data.audioError) {
        toast({ title: 'Audio unavailable', description: data.audioError });
      }

      if (data.audioBase64 && audioPlayerRef.current) {
         audioPlayerRef.current.src = `data:audio/mp3;base64,${data.audioBase64}`;
         audioPlayerRef.current.play();
      }

      if (data.interviewResponse.status === 'COMPLETED') {
        setIsCompleted(true);
        toast({ title: 'Interview Complete!', description: 'Generating your final report...' });
      } else {
        setMessages(prev => [...prev, data.interviewResponse.latestMessage]);
        setCurrentTurn(data.interviewResponse.currentTurn);
      }
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to process audio message.', variant: 'destructive' });
    } finally {
      setIsLoading(false);
    }
  };

  const getPersonaIcon = (persona?: string) => {
    switch (persona) {
      case 'TECH_LEAD': return <Cpu className="h-5 w-5 text-indigo-500" />;
      case 'HIRING_MANAGER': return <Building2 className="h-5 w-5 text-amber-500" />;
      case 'HR_AGENT': return <UserCheck className="h-5 w-5 text-emerald-500" />;
      default: return <Brain className="h-5 w-5 text-primary" />;
    }
  };

  const getPersonaName = (persona?: string) => {
    switch (persona) {
      case 'TECH_LEAD': return 'Tech Lead (Engineering)';
      case 'HIRING_MANAGER': return 'Hiring Manager (Team Lead)';
      case 'HR_AGENT': return 'HR Specialist (Culture)';
      default: return 'Interview Panel';
    }
  };

  if (!isStarted) {
    return (
      <div className="min-h-screen bg-muted/30 flex items-center justify-center p-4">
        <Card className="max-w-xl w-full shadow-2xl border-none">
          <CardHeader className="text-center space-y-2">
            <div className="mx-auto h-16 w-16 bg-primary/10 rounded-full flex items-center justify-center mb-4">
              <Brain className="h-10 w-10 text-primary" />
            </div>
            <CardTitle className="text-3xl font-black">AI Mock Interview</CardTitle>
            <p className="text-muted-foreground">
              Face a professional panel of 3 AI agents in a multi-turn simulation.
            </p>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="space-y-2">
              <label className="text-sm font-bold uppercase tracking-widest text-muted-foreground">Target Role</label>
              <Input 
                placeholder="e.g. Senior Java Developer, Product Manager..." 
                className="h-12 text-lg"
                value={targetRole}
                onChange={(e) => setTargetRole(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-bold uppercase tracking-widest text-muted-foreground">Your Resume (Optional)</label>
              <select 
                className="w-full h-12 rounded-md border bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-primary/20"
                value={selectedResumeId}
                onChange={(e) => setSelectedResumeId(e.target.value)}
              >
                <option value="">Personalize with my resume...</option>
                {resumes.map(r => (
                  <option key={r.id} value={r.id}>{r.title}</option>
                ))}
              </select>
            </div>
            <Button className="w-full h-14 text-lg font-bold" onClick={startInterview} disabled={isLoading}>
              {isLoading ? <Loader2 className="h-6 w-6 animate-spin mr-2" /> : "Deploy Interview Panel"}
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-muted/30 flex flex-col">
      <header className="bg-background border-b h-20 sticky top-0 z-10">
        <div className="container mx-auto px-4 h-full flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Button variant="ghost" size="icon" onClick={() => navigate('/dashboard')}>
              <ArrowLeft className="h-5 w-5" />
            </Button>
            <div>
              <h1 className="font-bold text-lg leading-tight">Mock Interview Simulation</h1>
              <p className="text-xs text-muted-foreground uppercase font-black tracking-widest">{targetRole}</p>
            </div>
          </div>
          <div className="flex items-center gap-6">
            <div className="hidden md:flex flex-col items-end">
              <span className="text-[10px] font-black uppercase text-muted-foreground">Progress</span>
              <div className="flex gap-1 mt-1">
                {[...Array(10)].map((_, i) => (
                  <div key={i} className={`h-1.5 w-4 rounded-full ${i < currentTurn ? 'bg-primary' : 'bg-muted'}`} />
                ))}
              </div>
            </div>
            <Badge variant="outline" className="h-8 px-4 text-xs font-bold uppercase tracking-tighter border-primary/20 bg-primary/5">
              Turn {currentTurn} / 10
            </Badge>
          </div>
        </div>
      </header>

      <main className="flex-1 container mx-auto px-4 max-w-4xl py-8 flex flex-col h-[calc(100vh-80px)]">
        <div 
          ref={scrollRef}
          className="flex-1 overflow-y-auto space-y-8 pr-4 scroll-smooth"
        >
          {messages.map((msg, i) => (
            <div key={i} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
              <div className={`max-w-[80%] flex gap-4 ${msg.role === 'user' ? 'flex-row-reverse' : 'flex-row'}`}>
                <div className={`h-10 w-10 rounded-full flex items-center justify-center shrink-0 shadow-lg ${
                  msg.role === 'user' ? 'bg-slate-900 text-white' : 'bg-white border'
                }`}>
                  {msg.role === 'user' ? <User className="h-5 w-5" /> : getPersonaIcon(msg.persona)}
                </div>
                <div className="space-y-1">
                  <div className={`flex items-center gap-2 mb-1 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                    <span className="text-[10px] font-black uppercase text-muted-foreground tracking-widest">
                      {msg.role === 'user' ? 'Candidate (You)' : getPersonaName(msg.persona)}
                    </span>
                  </div>
                  <div className={`p-5 rounded-2xl shadow-sm text-sm leading-relaxed ${
                    msg.role === 'user' 
                      ? 'bg-primary text-primary-foreground rounded-tr-none' 
                      : 'bg-white border rounded-tl-none'
                  }`}>
                    {msg.content}
                  </div>
                </div>
              </div>
            </div>
          ))}
          {isLoading && (
            <div className="flex justify-start">
              <div className="bg-white border p-4 rounded-2xl shadow-sm flex items-center gap-3">
                <Loader2 className="h-4 w-4 animate-spin text-primary" />
                <span className="text-xs font-medium text-muted-foreground">Panel is deliberating...</span>
              </div>
            </div>
          )}
          {isCompleted && (
            <div className="py-12 flex flex-col items-center justify-center text-center space-y-6">
              <div className="h-20 w-20 bg-emerald-500/10 rounded-full flex items-center justify-center">
                <Trophy className="h-10 w-10 text-emerald-500" />
              </div>
              <div>
                <h2 className="text-2xl font-bold">Interview Concluded</h2>
                <p className="text-muted-foreground">The panel has gathered enough data to provide a full report.</p>
              </div>
              <Button onClick={() => navigate(`/ai/mock-interview/${sessionId}/report`)} className="px-10 h-12 rounded-full font-bold">
                View Feedback Report
              </Button>
            </div>
          )}
        </div>
        {/* Hidden Audio Player for Agent TTS */}
        <audio ref={audioPlayerRef} className="hidden" />

        {!isCompleted && (
          <div className="mt-8 bg-background border p-2 rounded-2xl shadow-2xl flex gap-2 items-center">
            <Button 
              variant={isRecording ? "destructive" : "secondary"}
              size="icon" 
              className={`h-12 w-12 rounded-xl shrink-0 transition-all ${isRecording ? 'animate-pulse ring-4 ring-red-500/20' : ''}`}
              onClick={toggleRecording}
              disabled={isLoading}
            >
              {isRecording ? <Square className="h-5 w-5 fill-current" /> : <Mic className="h-5 w-5" />}
            </Button>
            <Input 
              placeholder={isRecording ? "Recording... Click stop when finished." : "Type your response or use microphone..."}
              className="flex-1 h-12 border-none focus-visible:ring-0 text-lg px-4"
              value={userInput}
              onChange={(e) => setUserInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSendMessage()}
              disabled={isLoading || isRecording}
            />
            <Button size="icon" className="h-12 w-12 rounded-xl shrink-0" onClick={handleSendMessage} disabled={isLoading || isRecording || !userInput.trim()}>
              <Send className="h-5 w-5" />
            </Button>
          </div>
        )}
      </main>
    </div>
  );
}
