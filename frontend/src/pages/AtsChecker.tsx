import React, { useState, useRef } from 'react';
import api from '@/lib/api';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { useToast } from '@/hooks/use-toast';
import { 
  Upload, 
  FileText, 
  Sparkles, 
  ShieldCheck, 
  Zap, 
  FileSearch, 
  ChevronRight,
  ArrowLeft,
  Loader2
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { AtsAnalysisView } from '@/components/resume/AtsAnalysisView';
import { useNavigate } from 'react-router-dom';

export default function AtsChecker() {
  const [file, setFile] = useState<File | null>(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [step, setStep] = useState(0);
  const [result, setResult] = useState<any>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { toast } = useToast();
  const navigate = useNavigate();

  const steps = [
    "Extracting raw document text...",
    "Scanning structural components...",
    "Analyzing keyword density...",
    "Evaluating formatting compatibility...",
    "Generating AI improvement strategy..."
  ];

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0];
    if (selectedFile) {
      if (!['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'].includes(selectedFile.type)) {
        toast({ title: 'Invalid file', description: 'Please upload a PDF or DOCX file.', variant: 'destructive' });
        return;
      }
      setFile(selectedFile);
      setResult(null);
    }
  };

  const handleStartAnalysis = async () => {
    if (!file) return;

    setAnalyzing(true);
    setStep(0);

    // Simulate progress steps
    const interval = setInterval(() => {
      setStep(prev => (prev < steps.length - 1 ? prev + 1 : prev));
    }, 1500);

    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('title', `ATS_Check_${new Date().getTime()}`);

      // 1. Upload and Parse (Backend flow handles this)
      // Pass isTransient=true so it doesn't show up in the main Resumes list
      const uploadRes = await api.post('/api/v1/resumes/upload?isTransient=true', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });

      const resumeId = uploadRes.data.data.id;

      // 2. Trigger ATS Analysis
      const analysisRes = await api.post(`/api/v1/resumes/${resumeId}/analyze-ats`);
      
      clearInterval(interval);
      setResult(analysisRes.data.data);
    } catch (error) {
      clearInterval(interval);
      toast({ title: 'Analysis Failed', description: 'Failed to process the document. Please try again.', variant: 'destructive' });
    } finally {
      setAnalyzing(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50/50 pb-20 relative overflow-hidden">
      {/* Background Effects */}
      <div className="absolute top-0 left-0 w-full h-[500px] bg-gradient-to-b from-indigo-50 via-white to-transparent -z-10" />
      <div className="absolute top-[-20%] left-[-10%] w-[50%] h-[500px] bg-indigo-200/40 rounded-full blur-[120px] -z-10 mix-blend-multiply" />
      <div className="absolute top-[-10%] right-[-5%] w-[40%] h-[400px] bg-emerald-200/30 rounded-full blur-[100px] -z-10 mix-blend-multiply" />

      <div className="max-w-6xl mx-auto space-y-8 px-4 pt-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <Button variant="ghost" onClick={() => navigate(-1)} className="gap-2 text-slate-600 hover:text-slate-900 hover:bg-white/50 backdrop-blur-sm rounded-xl">
            <ArrowLeft className="h-4 w-4" /> Back to Dashboard
          </Button>
          <div className="flex items-center gap-2 text-sm font-bold text-indigo-700 bg-white/80 backdrop-blur-md px-5 py-2.5 rounded-full border border-indigo-100 shadow-sm shadow-indigo-100/50">
             <ShieldCheck className="h-4 w-4 text-indigo-500" /> 
             <span>SECURE AI PROCESSING</span>
          </div>
        </div>

      {!result && !analyzing ? (
        <div className="flex flex-col items-center text-center space-y-16 py-16">
          <div className="space-y-6 relative">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-indigo-100/50 text-indigo-700 font-medium text-sm mb-4 border border-indigo-200/50 backdrop-blur-sm">
              <Sparkles className="h-4 w-4" /> Powered by GPT-4o
            </div>
            <h1 className="text-6xl font-black tracking-tight text-slate-900 drop-shadow-sm">
              Is your resume <span className="text-transparent bg-clip-text bg-gradient-to-r from-indigo-600 to-violet-600">Bot-Proof?</span>
            </h1>
            <p className="text-xl text-slate-600 max-w-2xl mx-auto leading-relaxed">
              Our advanced AI simulates real-world Applicant Tracking Systems to identify structural flaws, keyword gaps, and formatting issues before you apply.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-8 w-full max-w-5xl">
             <div className="p-8 bg-white/70 backdrop-blur-xl rounded-[2rem] border border-white shadow-[0_8px_30px_rgb(0,0,0,0.04)] hover:shadow-[0_8px_30px_rgb(79,70,229,0.1)] transition-all duration-300 space-y-4 group">
                <div className="h-14 w-14 bg-gradient-to-br from-indigo-50 to-indigo-100 text-indigo-600 rounded-2xl flex items-center justify-center group-hover:scale-110 transition-transform duration-300 shadow-inner">
                  <Zap className="h-7 w-7" />
                </div>
                <h3 className="text-xl font-bold text-slate-800">Instant Scoring</h3>
                <p className="text-slate-500 leading-relaxed">Get a 0-100 compatibility score in seconds based on industry standards.</p>
             </div>
             <div className="p-8 bg-white/70 backdrop-blur-xl rounded-[2rem] border border-white shadow-[0_8px_30px_rgb(0,0,0,0.04)] hover:shadow-[0_8px_30px_rgb(16,185,129,0.1)] transition-all duration-300 space-y-4 group">
                <div className="h-14 w-14 bg-gradient-to-br from-emerald-50 to-emerald-100 text-emerald-600 rounded-2xl flex items-center justify-center group-hover:scale-110 transition-transform duration-300 shadow-inner">
                  <FileSearch className="h-7 w-7" />
                </div>
                <h3 className="text-xl font-bold text-slate-800">Deep Structural Scan</h3>
                <p className="text-slate-500 leading-relaxed">We detect multi-column layouts, nested tables, and symbols that break parsers.</p>
             </div>
             <div className="p-8 bg-white/70 backdrop-blur-xl rounded-[2rem] border border-white shadow-[0_8px_30px_rgb(0,0,0,0.04)] hover:shadow-[0_8px_30px_rgb(245,158,11,0.1)] transition-all duration-300 space-y-4 group">
                <div className="h-14 w-14 bg-gradient-to-br from-amber-50 to-amber-100 text-amber-600 rounded-2xl flex items-center justify-center group-hover:scale-110 transition-transform duration-300 shadow-inner">
                  <Sparkles className="h-7 w-7" />
                </div>
                <h3 className="text-xl font-bold text-slate-800">AI Fix Recommendations</h3>
                <p className="text-slate-500 leading-relaxed">Receive specific, actionable instructions to optimize your formatting and content.</p>
             </div>
          </div>

          {/* Upload Zone */}
          <div 
            onClick={() => fileInputRef.current?.click()}
            className={`relative w-full max-w-3xl p-14 rounded-[3rem] transition-all duration-500 cursor-pointer group flex flex-col items-center gap-8 overflow-hidden
              ${file 
                ? 'bg-indigo-50/50 border-2 border-indigo-400 shadow-[0_0_40px_rgb(79,70,229,0.15)]' 
                : 'bg-white/50 border-2 border-dashed border-slate-300 hover:border-indigo-400 hover:bg-white/80 hover:shadow-2xl'
              }`}
          >
            {/* Ambient background glow for upload zone */}
            <div className="absolute inset-0 bg-gradient-to-b from-transparent to-white/20 pointer-events-none" />
            
            <input 
              type="file" 
              ref={fileInputRef} 
              className="hidden" 
              accept=".pdf,.docx" 
              onChange={handleFileChange} 
            />
            
            <div className={`relative z-10 h-28 w-28 rounded-full flex items-center justify-center transition-all duration-500 ${file ? 'bg-gradient-to-br from-indigo-500 to-violet-600 text-white shadow-xl scale-110' : 'bg-slate-100 text-slate-400 group-hover:bg-indigo-50 group-hover:text-indigo-500 group-hover:scale-110 shadow-sm'}`}>
              <Upload className="h-12 w-12" />
              {file && (
                <div className="absolute inset-0 rounded-full border-4 border-indigo-300/30 animate-ping" />
              )}
            </div>

            <div className="space-y-3 relative z-10">
              <h3 className="text-3xl font-black text-slate-800 tracking-tight">
                {file ? file.name : 'Drop your resume here'}
              </h3>
              <p className="text-slate-500 font-medium text-lg">
                {file ? 'Ready for deep scan' : 'PDF or Word files supported (max 5MB)'}
              </p>
            </div>

            {file && (
              <Button 
                onClick={(e) => {
                  e.stopPropagation();
                  handleStartAnalysis();
                }}
                className="relative z-10 h-16 px-12 rounded-full bg-gradient-to-r from-indigo-600 to-violet-600 hover:from-indigo-700 hover:to-violet-700 text-white font-bold text-xl gap-3 shadow-[0_10px_30px_rgb(79,70,229,0.3)] hover:shadow-[0_10px_40px_rgb(79,70,229,0.5)] transition-all transform hover:-translate-y-1"
              >
                Launch Diagnostic Scan <ChevronRight className="h-6 w-6" />
              </Button>
            )}
          </div>
        </div>
      ) : analyzing ? (
        <div className="flex flex-col items-center justify-center py-32 space-y-12">
           <div className="relative h-40 w-40">
              <motion.div 
                animate={{ rotate: 360 }}
                transition={{ duration: 3, repeat: Infinity, ease: "linear" }}
                className="absolute inset-0 border-4 border-indigo-100/50 border-t-indigo-600 rounded-full"
              />
              <motion.div 
                animate={{ rotate: -360 }}
                transition={{ duration: 4, repeat: Infinity, ease: "linear" }}
                className="absolute inset-4 border-4 border-violet-100/50 border-b-violet-500 rounded-full"
              />
              <div className="absolute inset-0 flex items-center justify-center">
                 <div className="h-16 w-16 bg-gradient-to-br from-indigo-500 to-violet-600 rounded-2xl flex items-center justify-center shadow-lg shadow-indigo-200 animate-pulse">
                    <FileSearch className="h-8 w-8 text-white" />
                 </div>
              </div>
           </div>
           <div className="space-y-4 text-center max-w-md">
              <h2 className="text-3xl font-black text-slate-900 tracking-tight">AI Diagnostic Engine</h2>
              <div className="h-12 bg-white/60 backdrop-blur-sm rounded-full flex items-center justify-center px-8 border border-white shadow-sm">
                <motion.p 
                  key={step}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -10 }}
                  className="text-indigo-700 font-bold text-lg"
                >
                  {steps[step]}
                </motion.p>
              </div>
           </div>
        </div>
      ) : (
        <div className="space-y-8 animate-in fade-in zoom-in duration-500">
           <div className="flex items-center justify-between bg-white/70 backdrop-blur-xl p-6 rounded-[2rem] border border-white shadow-sm">
              <div className="space-y-1">
                 <h2 className="text-3xl font-black text-slate-900 tracking-tight">Diagnostic Report</h2>
                 <p className="text-slate-500 font-medium flex items-center gap-2">
                   <FileText className="h-4 w-4 text-indigo-400" /> {file?.name}
                 </p>
              </div>
              <Button onClick={() => {
                setResult(null);
                setFile(null);
              }} className="rounded-full h-12 px-6 bg-slate-900 hover:bg-slate-800 text-white font-bold transition-transform hover:scale-105">
                Scan New Document
              </Button>
           </div>

           <div className="bg-white/50 backdrop-blur-md rounded-[2.5rem] p-4 border border-white shadow-xl shadow-slate-200/50">
             <AtsAnalysisView 
                score={result.atsScore || 0}
                issues={result.atsIssues?.issues || []}
                suggestions={result.atsIssues?.suggestions || []}
                structuralFlags={result.atsIssues?.structuralFlags || {}}
                onFixWithAi={() => {
                  toast({ title: "Coming Soon", description: "The AI Auto-Fixer is being prepared." });
                }}
             />
           </div>
        </div>
      )}
    </div>
  </div>
  );
}
