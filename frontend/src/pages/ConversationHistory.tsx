import { useState, useEffect } from 'react';
import api from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { ArrowLeft, MessageSquare, Bot, User, Clock } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import { useNavigate } from 'react-router-dom';

interface ConversationHistory {
  id: string;
  userId: string;
  jobId: string | null;
  resumeId: string | null;
  role: string;
  content: string;
  agentPersona: string | null;
  createdAt: string;
}

export default function ConversationHistory() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const [history, setHistory] = useState<ConversationHistory[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<'all' | 'user' | 'assistant'>('all');

  useEffect(() => {
    fetchHistory();
  }, []);

  const fetchHistory = async () => {
    try {
      const res = await api.get('/api/v1/ai/conversation-history');
      setHistory(res.data.data);
    } catch (error) {
      toast({ title: 'Failed to load conversation history', variant: 'destructive' });
    } finally {
      setLoading(false);
    }
  };

  const filteredHistory = filter === 'all' 
    ? history 
    : history.filter(h => h.role.toLowerCase() === filter);

  return (
    <div className="min-h-screen bg-slate-50/50 pb-20">
      <div className="bg-white border-b sticky top-0 z-20">
        <div className="container mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Button variant="ghost" size="icon" onClick={() => navigate('/dashboard')}>
              <ArrowLeft className="h-5 w-5" />
            </Button>
            <div>
              <h1 className="text-xl font-black text-slate-900 flex items-center gap-2">
                <MessageSquare className="h-5 w-5 text-indigo-600" /> Conversation History
              </h1>
              <p className="text-xs text-slate-400 font-medium">Your AI interactions over time</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant={filter === 'all' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setFilter('all')}
            >
              All
            </Button>
            <Button
              variant={filter === 'user' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setFilter('user')}
            >
              <User className="h-4 w-4 mr-1" /> You
            </Button>
            <Button
              variant={filter === 'assistant' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setFilter('assistant')}
            >
              <Bot className="h-4 w-4 mr-1" /> AI
            </Button>
          </div>
        </div>
      </div>

      <div className="container mx-auto px-4 py-8 max-w-4xl">
        {loading ? (
          <div className="text-center py-12 text-slate-500">Loading conversation history...</div>
        ) : filteredHistory.length === 0 ? (
          <Card className="text-center py-12">
            <CardContent>
              <MessageSquare className="h-12 w-12 text-slate-300 mx-auto mb-4" />
              <h3 className="text-lg font-bold text-slate-700 mb-2">No Conversations Yet</h3>
              <p className="text-slate-500">Start using AI features to see your conversation history here.</p>
            </CardContent>
          </Card>
        ) : (
          <div className="space-y-4">
            {filteredHistory.map((conv) => (
              <Card key={conv.id} className={conv.role === 'user' ? 'bg-blue-50/30 border-blue-100' : 'bg-indigo-50/30 border-indigo-100'}>
                <CardContent className="p-6">
                  <div className="flex items-start gap-4">
                    <div className={`h-10 w-10 rounded-full flex items-center justify-center shrink-0 ${
                      conv.role === 'user' 
                        ? 'bg-blue-500 text-white' 
                        : 'bg-indigo-500 text-white'
                    }`}>
                      {conv.role === 'user' ? <User className="h-5 w-5" /> : <Bot className="h-5 w-5" />}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-2">
                        <Badge variant={conv.role === 'user' ? 'default' : 'secondary'}>
                          {conv.role === 'user' ? 'You' : 'AI Assistant'}
                        </Badge>
                        {conv.agentPersona && (
                          <Badge variant="outline" className="text-xs">
                            {conv.agentPersona}
                          </Badge>
                        )}
                        <div className="flex items-center gap-1 text-xs text-slate-400 ml-auto">
                          <Clock className="h-3 w-3" />
                          {new Date(conv.createdAt).toLocaleString()}
                        </div>
                      </div>
                      <p className="text-slate-700 leading-relaxed whitespace-pre-wrap">{conv.content}</p>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
