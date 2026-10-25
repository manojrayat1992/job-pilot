import { AiFeedback } from '@/components/ai/AiFeedback';
import { Button } from '@/components/ui/button';
import { Card,CardContent,CardDescription,CardHeader,CardTitle } from '@/components/ui/card';
import { Textarea } from '@/components/ui/textarea';
import { useToast } from '@/hooks/use-toast';
import api from '@/lib/api';
import { Job,Resume } from '@/types';
import { motion } from 'framer-motion';
import {
ArrowLeft,
Brain,
Lightbulb,
Loader2,
MessageSquare,
Mic,
Sparkles
} from 'lucide-react';
import { useEffect,useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function InterviewPrep() {
  const navigate = useNavigate();
  const { toast } = useToast();
  
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [selectedResumeId, setSelectedResumeId] = useState<string>('');
  const [selectedJobId, setSelectedJobId] = useState<string>('');
  const [question, setQuestion] = useState('');
  
  const [isLoading, setIsLoading] = useState(false);
  const [answer, setAnswer] = useState('');
  const [tips, setTips] = useState<string[]>([]);
  const [aiLogId, setAiLogId] = useState<string | null>(null);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [resumesRes, jobsRes] = await Promise.all([
        api.get('/api/v1/resumes'),
        api.get('/api/v1/jobs')
      ]);
      setResumes(resumesRes.data.data.content);
      setJobs(jobsRes.data.data.content);
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to fetch data.', variant: 'destructive' });
    }
  };

  const [isRecording, setIsRecording] = useState(false);
  const [transcription, setTranscription] = useState('');
  const [isTranscribing, setIsTranscribing] = useState(false);
  const [mediaRecorder, setMediaRecorder] = useState<MediaRecorder | null>(null);

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream);
      const chunks: Blob[] = [];

      recorder.ondataavailable = (e) => chunks.push(e.data);
      recorder.onstop = async () => {
        const audioBlob = new Blob(chunks, { type: 'audio/webm' });
        await handleTranscription(audioBlob);
        stream.getTracks().forEach(track => track.stop());
      };

      recorder.start();
      setMediaRecorder(recorder);
      setIsRecording(true);
    } catch (err) {
      toast({ title: 'Microphone Error', description: 'Could not access microphone.', variant: 'destructive' });
    }
  };

  const stopRecording = () => {
    if (mediaRecorder) {
      mediaRecorder.stop();
      setIsRecording(false);
    }
  };

  const handleTranscription = async (blob: Blob) => {
    setIsTranscribing(true);
    try {
      const formData = new FormData();
      formData.append('file', blob, 'practice.webm');

      const response = await api.post('/api/v1/ai/transcribe', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      setTranscription(response.data.data);
    } catch (err) {
      toast({ title: 'Transcription Error', description: 'Failed to transcribe audio.', variant: 'destructive' });
    } finally {
      setIsTranscribing(false);
    }
  };

  const handleAnswer = async () => {
    if (!question) {
      toast({ title: 'Error', description: 'Please enter a question.', variant: 'destructive' });
      return;
    }

    setIsLoading(true);
    try {
      const job = jobs.find(j => j.id === selectedJobId);
      const response = await api.post('/api/v1/ai/answer-question', {
        resumeId: selectedResumeId || undefined,
        jobText: job?.rawText || undefined,
        question: question
      });
      setAnswer(response.data.data.answer);
      setTips(response.data.data.tips || []);
      setAiLogId(response.data.data.aiLogId);
      toast({ title: 'Success', description: 'Answer generated!' });
    } catch (err: any) {
      toast({ title: 'Error', description: 'Failed to generate answer.', variant: 'destructive' });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-muted/30 pb-20">
      <div className="bg-background border-b sticky top-0 z-10">
        <div className="container mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Button variant="ghost" size="icon" onClick={() => navigate('/dashboard')}>
              <ArrowLeft className="h-5 w-5" />
            </Button>
            <h1 className="text-xl font-bold">AI Interview Coach</h1>
          </div>
        </div>
      </div>

      <div className="container mx-auto px-4 py-8 max-w-6xl">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          
          <div className="lg:col-span-4 space-y-6">
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Coach Settings</CardTitle>
                <CardDescription>Provide context for better answers.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-muted-foreground">Resume (Optional)</label>
                  <select 
                    className="w-full p-2 rounded-md border bg-background text-sm outline-none focus:ring-2 focus:ring-primary/20"
                    value={selectedResumeId}
                    onChange={(e) => setSelectedResumeId(e.target.value)}
                  >
                    <option value="">Select a resume...</option>
                    {resumes.map(r => (
                      <option key={r.id} value={r.id}>{r.title}</option>
                    ))}
                  </select>
                </div>

                <div className="space-y-2">
                  <label className="text-sm font-semibold text-muted-foreground">Target Job (Optional)</label>
                  <select 
                    className="w-full p-2 rounded-md border bg-background text-sm outline-none focus:ring-2 focus:ring-primary/20"
                    value={selectedJobId}
                    onChange={(e) => setSelectedJobId(e.target.value)}
                  >
                    <option value="">Select a job...</option>
                    {jobs.map(j => (
                      <option key={j.id} value={j.id}>{j.company} - {j.title}</option>
                    ))}
                  </select>
                </div>

                <div className="pt-4 border-t">
                  <h4 className="text-sm font-bold mb-3 flex items-center gap-2 uppercase tracking-wider text-muted-foreground">
                    <Mic className="h-4 w-4" /> The Question
                  </h4>
                  <Textarea 
                    placeholder="e.g. Tell me about a time you failed and how you handled it."
                    className="min-h-[120px] text-sm"
                    value={question}
                    onChange={(e) => setQuestion(e.target.value)}
                  />
                </div>

                <Button 
                  onClick={handleAnswer} 
                  className="w-full mt-4 h-11" 
                  disabled={isLoading || !question}
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      Analyzing...
                    </>
                  ) : (
                    <>
                      <Sparkles className="mr-2 h-4 w-4" />
                      Get AI Answer
                    </>
                  )}
                </Button>
              </CardContent>
            </Card>

            {tips.length > 0 && (
              <Card className="bg-yellow-500/5 border-yellow-500/20">
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm font-bold flex items-center gap-2 text-yellow-700">
                    <Lightbulb className="h-4 w-4" /> Pro Coaching Tips
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <ul className="space-y-3">
                    {tips.map((tip, i) => (
                      <li key={i} className="text-xs text-yellow-800 flex gap-2">
                        <div className="h-1.5 w-1.5 bg-yellow-500 rounded-full mt-1 shrink-0" />
                        {tip}
                      </li>
                    ))}
                  </ul>
                </CardContent>
              </Card>
            )}
          </div>

          <div className="lg:col-span-8">
            {!answer && !isLoading ? (
              <div className="h-full min-h-[500px] flex flex-col items-center justify-center bg-background rounded-xl border-2 border-dashed p-12 text-center">
                <div className="h-20 w-20 rounded-full bg-primary/10 flex items-center justify-center mb-6">
                  <Brain className="h-10 w-10 text-primary" />
                </div>
                <h2 className="text-2xl font-bold mb-2">Practice with the AI Coach</h2>
                <p className="text-muted-foreground max-w-md">
                  Enter an interview question to receive a high-impact, tailored answer based on your actual experience and the target job description.
                </p>
              </div>
            ) : isLoading ? (
              <div className="h-full min-h-[500px] flex flex-col items-center justify-center bg-background rounded-xl border p-12 space-y-6">
                <Loader2 className="h-10 w-10 animate-spin text-primary" />
                <p className="text-muted-foreground">Synthesizing your experience into the perfect answer...</p>
              </div>
            ) : (
              <div className="space-y-6">
                <Card className="min-h-[400px]">
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                      <Sparkles className="h-5 w-5 text-primary" />
                      Suggested Response
                    </CardTitle>
                  </CardHeader>
                  <CardContent>
                    <div className="p-6 bg-muted/30 rounded-xl text-lg leading-relaxed text-slate-800 whitespace-pre-wrap">
                      {answer}
                    </div>
                    {aiLogId && <AiFeedback aiLogId={aiLogId} />}
                  </CardContent>
                </Card>

                <div className="flex flex-col items-center justify-center gap-6 p-8 border-2 border-dashed rounded-xl bg-slate-50/50">
                  <div className="text-center space-y-4 w-full max-w-2xl">
                    <p className="text-sm font-black mb-4 text-slate-400 uppercase tracking-[0.2em]">Practice Mode</p>
                    
                    {isRecording ? (
                      <div className="flex flex-col items-center gap-4">
                        <div className="flex items-center gap-2 px-4 py-2 bg-red-50 text-red-600 rounded-full animate-pulse font-bold text-sm border border-red-100">
                          <div className="h-2 w-2 bg-red-600 rounded-full" />
                          RECORDING YOUR ANSWER...
                        </div>
                        <Button 
                          variant="destructive" 
                          className="rounded-full px-12 h-14 gap-3 shadow-lg shadow-red-500/20"
                          onClick={stopRecording}
                        >
                          <div className="h-4 w-4 bg-white rounded-sm" /> Stop & Analyze
                        </Button>
                      </div>
                    ) : isTranscribing ? (
                      <div className="flex flex-col items-center gap-4">
                        <Loader2 className="h-10 w-10 animate-spin text-primary" />
                        <p className="text-sm font-bold text-slate-500">Transcribing your voice with AI...</p>
                      </div>
                    ) : (
                      <Button 
                        variant="outline" 
                        className="rounded-full px-12 h-14 gap-3 border-2 hover:bg-slate-50 hover:border-primary/50 transition-all font-bold text-lg"
                        onClick={startRecording}
                      >
                        <Mic className="h-6 w-6 text-primary" /> Start Recording Practice
                      </Button>
                    )}

                    {transcription && (
                      <motion.div 
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        className="mt-8 text-left space-y-3"
                      >
                        <h4 className="text-sm font-black text-slate-400 uppercase tracking-widest flex items-center gap-2">
                          <MessageSquare className="h-4 w-4" /> Your Practice Attempt
                        </h4>
                        <div className="p-6 bg-white border border-slate-100 rounded-2xl shadow-sm text-slate-600 leading-relaxed italic">
                          "{transcription}"
                        </div>
                      </motion.div>
                    )}
                  </div>
                </div>
              </div>
            )}
          </div>

        </div>
      </div>
    </div>
  );
}

