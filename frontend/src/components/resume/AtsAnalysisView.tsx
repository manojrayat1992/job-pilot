import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { 
  AlertCircle, 
  CheckCircle2, 
  AlertTriangle, 
  Sparkles, 
  ChevronDown, 
  ChevronUp,
  Layout,
  Type,
  FileSearch
} from 'lucide-react';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";

interface AtsIssue {
  category: string;
  description: string;
  severity: 'High' | 'Medium' | 'Low';
}

interface AtsAnalysisProps {
  score: number;
  issues: AtsIssue[];
  suggestions: string[];
  structuralFlags: Record<string, any>;
  onFixWithAi: () => void;
}

export const AtsAnalysisView: React.FC<AtsAnalysisProps> = ({ 
  score, 
  issues, 
  suggestions, 
  structuralFlags,
  onFixWithAi 
}) => {
  const getScoreColor = (s: number) => {
    if (s >= 80) return 'text-emerald-500';
    if (s >= 60) return 'text-amber-500';
    return 'text-rose-500';
  };

  const getSeverityBadge = (sev: string) => {
    switch (sev) {
      case 'High': return <Badge variant="destructive" className="text-[10px] h-5">Critical</Badge>;
      case 'Medium': return <Badge variant="outline" className="text-amber-600 border-amber-200 bg-amber-50 text-[10px] h-5">Warning</Badge>;
      default: return <Badge variant="secondary" className="text-[10px] h-5">Suggestion</Badge>;
    }
  };

  const getCategoryIcon = (cat: string) => {
    switch (cat) {
      case 'Structural': return <Layout className="h-4 w-4" />;
      case 'Formatting': return <Type className="h-4 w-4" />;
      default: return <FileSearch className="h-4 w-4" />;
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500">
      {/* Score Header */}
      <div className="flex items-center justify-between bg-slate-50 p-6 rounded-2xl border border-slate-100">
        <div className="flex items-center gap-6">
          <div className="relative h-20 w-20 flex items-center justify-center">
             <svg className="h-20 w-20 -rotate-90">
                <circle
                  cx="40"
                  cy="40"
                  r="36"
                  fill="transparent"
                  stroke="currentColor"
                  strokeWidth="8"
                  className="text-slate-200"
                />
                <circle
                  cx="40"
                  cy="40"
                  r="36"
                  fill="transparent"
                  stroke="currentColor"
                  strokeWidth="8"
                  strokeDasharray={2 * Math.PI * 36}
                  strokeDashoffset={2 * Math.PI * 36 * (1 - score / 100)}
                  strokeLinecap="round"
                  className={getScoreColor(score)}
                />
             </svg>
             <span className={`absolute text-xl font-black ${getScoreColor(score)}`}>{score}</span>
          </div>
          <div>
            <h3 className="text-lg font-bold text-slate-800">ATS Compatibility Score</h3>
            <p className="text-sm text-slate-500">Based on standard Applicant Tracking System algorithms</p>
          </div>
        </div>
        <Button 
          onClick={onFixWithAi}
          className="bg-indigo-600 hover:bg-indigo-700 text-white gap-2 h-12 rounded-xl shadow-lg shadow-indigo-100"
        >
          <Sparkles className="h-4 w-4" /> Fix All with AI
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Detailed Issues */}
        <Card className="rounded-2xl border-slate-100 shadow-sm overflow-hidden">
          <CardHeader className="bg-slate-50/50 border-b border-slate-100">
            <CardTitle className="text-sm font-bold uppercase tracking-widest text-slate-400 flex items-center gap-2">
              <AlertCircle className="h-4 w-4" /> Detected Issues
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <Accordion type="single" collapsible className="w-full">
              {issues.map((issue, idx) => (
                <AccordionItem key={idx} value={`issue-${idx}`} className="border-b border-slate-100 last:border-0 px-6">
                  <AccordionTrigger className="hover:no-underline py-4">
                    <div className="flex items-center gap-3 text-left">
                      <div className="p-2 bg-slate-100 rounded-lg text-slate-600">
                        {getCategoryIcon(issue.category)}
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-bold text-sm text-slate-700">{issue.category}</span>
                          {getSeverityBadge(issue.severity)}
                        </div>
                        <p className="text-xs text-slate-500 line-clamp-1 mt-0.5">{issue.description}</p>
                      </div>
                    </div>
                  </AccordionTrigger>
                  <AccordionContent className="pb-4 text-sm text-slate-600 leading-relaxed">
                    {issue.description}
                  </AccordionContent>
                </AccordionItem>
              ))}
              {issues.length === 0 && (
                <div className="p-12 text-center text-slate-400">
                   <CheckCircle2 className="h-10 w-10 mx-auto mb-3 text-emerald-500/20" />
                   <p className="font-bold">No critical issues found!</p>
                </div>
              )}
            </Accordion>
          </CardContent>
        </Card>

        {/* AI Suggestions */}
        <Card className="rounded-2xl border-slate-100 shadow-sm overflow-hidden">
          <CardHeader className="bg-slate-50/50 border-b border-slate-100">
            <CardTitle className="text-sm font-bold uppercase tracking-widest text-slate-400 flex items-center gap-2">
              <Sparkles className="h-4 w-4" /> AI Suggestions
            </CardTitle>
          </CardHeader>
          <CardContent className="p-6">
             <ul className="space-y-4">
               {suggestions.map((s, idx) => (
                 <li key={idx} className="flex gap-3 text-sm text-slate-600 leading-relaxed group">
                   <div className="h-6 w-6 rounded-full bg-indigo-50 text-indigo-600 flex items-center justify-center text-[10px] font-black shrink-0 mt-0.5 group-hover:bg-indigo-600 group-hover:text-white transition-colors">
                     {idx + 1}
                   </div>
                   {s}
                 </li>
               ))}
               {suggestions.length === 0 && (
                 <li className="text-center py-8 text-slate-400 italic">No suggestions available yet.</li>
               )}
             </ul>
          </CardContent>
        </Card>
      </div>

      {/* Structural Flags */}
      <div className="grid grid-cols-3 gap-4">
         <div className={`p-4 rounded-xl border flex items-center gap-3 ${structuralFlags?.multiColumn ? 'bg-amber-50 border-amber-200 text-amber-700' : 'bg-emerald-50 border-emerald-200 text-emerald-700'}`}>
            <Layout className="h-5 w-5 shrink-0" />
            <div>
              <div className="text-[10px] font-black uppercase tracking-widest opacity-60">Layout</div>
              <div className="text-xs font-bold">{structuralFlags?.multiColumn ? 'Multi-Column detected' : 'Standard Layout'}</div>
            </div>
         </div>
         <div className={`p-4 rounded-xl border flex items-center gap-3 ${structuralFlags?.hasUnusualSymbols ? 'bg-amber-50 border-amber-200 text-amber-700' : 'bg-emerald-50 border-emerald-200 text-emerald-700'}`}>
            <Type className="h-5 w-5 shrink-0" />
            <div>
              <div className="text-[10px] font-black uppercase tracking-widest opacity-60">Typography</div>
              <div className="text-xs font-bold">{structuralFlags?.hasUnusualSymbols ? 'Unusual symbols found' : 'Standard Characters'}</div>
            </div>
         </div>
         <div className="p-4 rounded-xl border bg-slate-50 border-slate-200 text-slate-700 flex items-center gap-3">
            <AlertTriangle className="h-5 w-5 shrink-0" />
            <div>
              <div className="text-[10px] font-black uppercase tracking-widest opacity-60">Risk Level</div>
              <div className="text-xs font-bold">{score < 50 ? 'High' : score < 80 ? 'Moderate' : 'Low'}</div>
            </div>
         </div>
      </div>
    </div>
  );
};
