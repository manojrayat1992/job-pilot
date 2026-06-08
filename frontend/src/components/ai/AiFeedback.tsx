import React, { useState } from 'react';
import { ThumbsUp, ThumbsDown, Send, MessageSquare } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import api from '@/lib/api';
import { useToast } from '@/hooks/use-toast';

interface AiFeedbackProps {
  aiLogId: string;
}

export const AiFeedback: React.FC<AiFeedbackProps> = ({ aiLogId }) => {
  const [rating, setRating] = useState<number | null>(null);
  const [comment, setComment] = useState('');
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [showComment, setShowComment] = useState(false);
  const { toast } = useToast();

  const handleFeedback = async (score: number) => {
    setRating(score);
    if (score === 5) { // Thumbs up is 5, Thumbs down is 1
      submit(score, '');
    } else {
      setShowComment(true);
    }
  };

  const submit = async (score: number, text: string) => {
    try {
      await api.post('/api/v1/ai/feedback', {
        aiLogId,
        rating: score,
        comment: text
      });

      setIsSubmitted(true);
      toast({ title: "Feedback received", description: "Thanks for helping us improve!" });
    } catch (error) {
      toast({ 
        title: "Error", 
        description: "Failed to submit feedback.", 
        variant: "destructive" 
      });
    }
  };

  if (isSubmitted) {
    return (
      <div className="flex items-center gap-2 text-emerald-600 text-xs font-bold uppercase tracking-widest mt-4 animate-in fade-in slide-in-from-bottom-2">
        <ThumbsUp className="h-4 w-4" /> Feedback Saved
      </div>
    );
  }

  return (
    <div className="mt-6 border-t border-slate-100 pt-6">
      {!showComment ? (
        <div className="flex flex-col gap-4">
          <p className="text-[10px] font-black text-slate-400 uppercase tracking-[0.2em]">Rate this response</p>
          <div className="flex items-center gap-3">
            <Button 
              variant="outline" 
              size="sm" 
              className="rounded-xl hover:bg-emerald-50 hover:text-emerald-600 hover:border-emerald-200 gap-2 font-bold transition-all"
              onClick={() => handleFeedback(5)}
            >
              <ThumbsUp className="h-4 w-4" /> Helpful
            </Button>
            <Button 
              variant="outline" 
              size="sm" 
              className="rounded-xl hover:bg-rose-50 hover:text-rose-600 hover:border-rose-200 gap-2 font-bold transition-all"
              onClick={() => handleFeedback(1)}
            >
              <ThumbsDown className="h-4 w-4" /> Not Helpful
            </Button>
          </div>
        </div>
      ) : (
        <div className="space-y-4 animate-in fade-in slide-in-from-top-2">
          <div className="flex items-center justify-between">
             <p className="text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] flex items-center gap-2">
               <MessageSquare className="h-3 w-3" /> How can we improve?
             </p>
             <button onClick={() => setShowComment(false)} className="text-[10px] font-bold text-slate-400 hover:text-slate-600 uppercase">Cancel</button>
          </div>
          <Textarea 
            placeholder="Tell us what was wrong (optional)..."
            className="rounded-2xl border-slate-200 text-sm font-medium resize-none focus:ring-indigo-500"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
          />
          <Button 
            className="w-full h-12 rounded-2xl bg-indigo-600 text-white font-bold gap-2 shadow-lg shadow-indigo-100"
            onClick={() => submit(rating || 1, comment)}
          >
            Submit Feedback <Send className="h-4 w-4" />
          </Button>
        </div>
      )}
    </div>
  );
};
