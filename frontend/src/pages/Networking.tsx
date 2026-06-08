import { useState, useEffect } from 'react';
import api from '@/lib/api';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { 
  Search, 
  RotateCcw, 
  Linkedin, 
  Mail, 
  Lock, 
  Globe, 
  UserCircle,
  Building,
  Filter,
  CheckCircle2,
  ChevronRight,
  TrendingUp,
  Coins
} from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import { LoadingSpinner } from '@/components/loading/LoadingSpinner';
import { useAuthStore } from '@/store/authStore';

interface Contact {
  id: string;
  name: string;
  jobTitle: string;
  companyName: string;
  companyDomain: string;
  email: string;
  linkedinUrl: string;
  function: string;
  managementLevel: string;
  country: string;
  unlocked?: boolean;
}

export default function Networking() {
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isUnlocking, setIsUnlocking] = useState<string | null>(null);
  const [domain, setDomain] = useState('');
  const [selectedFunction, setSelectedFunction] = useState('All');
  const [selectedLevel, setSelectedLevel] = useState('All');
  const [selectedCountry, setSelectedCountry] = useState('All');
  const { user, setUser } = useAuthStore() as any;
  const { toast } = useToast();

  const functions = ['Human Resources', 'Engineering', 'Product', 'Sales', 'Marketing', 'Recruiting'];
  const levels = ['Founder', 'VP', 'Head', 'Director', 'Manager', 'Individual Contributor'];
  const countries = ['United States', 'India', 'United Kingdom', 'Canada', 'Germany'];

  const handleSearch = async () => {
    if (!domain && selectedFunction === 'All') {
       toast({ title: 'Input Required', description: 'Please enter a company domain or select a function.' });
       return;
    }
    
    setIsLoading(true);
    try {
      const res = await api.get('/api/v1/networking/search', {
        params: {
          domain: domain || undefined,
          function: selectedFunction !== 'All' ? selectedFunction : undefined,
          level: selectedLevel !== 'All' ? selectedLevel : undefined,
          country: selectedCountry !== 'All' ? selectedCountry : undefined
        }
      });
      setContacts(res.data.data);
    } catch (error) {
      toast({ title: 'Search Failed', description: 'Could not fetch contacts.', variant: 'destructive' });
    } finally {
      setIsLoading(false);
    }
  };

  const handleUnlock = async (contactId: string) => {
    if (user.credits <= 0) {
      toast({ title: 'No Credits', description: 'Please upgrade to unlock more contacts.', variant: 'destructive' });
      return;
    }
    
    setIsUnlocking(contactId);
    try {
      const res = await api.post(`/api/v1/networking/unlock/${contactId}`);
      const updatedContact = res.data.data;
      
      setContacts(prev => prev.map(c => c.id === contactId ? { ...updatedContact, unlocked: true } : c));
      
      // Update local user credits
      if (user) {
        setUser({ ...user, credits: user.credits - 1 });
      }
      
      toast({ title: 'Contact Unlocked', description: `Success! Email and LinkedIn are now visible.` });
    } catch (error) {
      toast({ title: 'Unlock Failed', description: 'Could not unlock contact details.', variant: 'destructive' });
    } finally {
      setIsUnlocking(null);
    }
  };

  return (
    <div className="flex h-[calc(100vh-120px)] bg-slate-50/50 rounded-2xl border overflow-hidden">
      {/* Sidebar Filters */}
      <aside className="w-80 border-r bg-white p-6 flex flex-col gap-8 overflow-y-auto">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-bold text-slate-800">Search Contacts</h2>
          <Button variant="ghost" size="icon" onClick={() => {
            setDomain('');
            setSelectedFunction('All');
            setSelectedLevel('All');
            setSelectedCountry('All');
          }}>
            <RotateCcw className="h-4 w-4 text-slate-400" />
          </Button>
        </div>

        <div className="flex items-center gap-2 px-3 py-1.5 bg-slate-50 rounded-lg border">
          <div className="flex items-center gap-2 text-indigo-600 font-bold">
            <Coins className="h-4 w-4" />
            <span>{user?.credits || 0}</span>
          </div>
          <Badge variant="secondary" className="text-[10px] font-black uppercase">Credits</Badge>
        </div>

        <div className="space-y-6">
          <div className="space-y-3">
            <label className="text-sm font-bold text-slate-600 uppercase tracking-wider">Company URL</label>
            <div className="relative">
              <Globe className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
              <Input 
                placeholder="e.g. google.com" 
                className="pl-10"
                value={domain}
                onChange={(e) => setDomain(e.target.value)}
              />
            </div>
          </div>

          <div className="space-y-3">
            <label className="text-sm font-bold text-slate-600 uppercase tracking-wider">Function</label>
            <div className="flex flex-wrap gap-2">
              <Badge 
                variant={selectedFunction === 'All' ? 'default' : 'outline'}
                className="cursor-pointer px-3 py-1"
                onClick={() => setSelectedFunction('All')}
              >All</Badge>
              {functions.map(f => (
                <Badge 
                  key={f}
                  variant={selectedFunction === f ? 'default' : 'outline'}
                  className="cursor-pointer px-3 py-1"
                  onClick={() => setSelectedFunction(f)}
                >{f}</Badge>
              ))}
            </div>
          </div>

          <div className="space-y-3">
            <label className="text-sm font-bold text-slate-600 uppercase tracking-wider">Management Level</label>
            <div className="flex flex-wrap gap-2">
              {levels.map(l => (
                <Badge 
                  key={l}
                  variant={selectedLevel === l ? 'default' : 'outline'}
                  className="cursor-pointer px-3 py-1"
                  onClick={() => setSelectedLevel(l)}
                >{l}</Badge>
              ))}
            </div>
          </div>

          <div className="space-y-3">
            <label className="text-sm font-bold text-slate-600 uppercase tracking-wider">Country</label>
            <select 
              className="w-full p-2 rounded-md border text-sm focus:ring-2 focus:ring-indigo-500"
              value={selectedCountry}
              onChange={(e) => setSelectedCountry(e.target.value)}
            >
              <option value="All">Select Countries</option>
              {countries.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
        </div>

        <Button 
          className="mt-auto bg-indigo-600 hover:bg-indigo-700 shadow-lg shadow-indigo-200 h-12 font-bold text-lg"
          onClick={handleSearch}
          disabled={isLoading}
        >
          <Search className="mr-2 h-5 w-5" />
          {isLoading ? 'Searching...' : 'Search'}
        </Button>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 bg-white overflow-y-auto">
        {contacts.length > 0 ? (
          <div className="p-0">
            <table className="w-full border-collapse">
              <thead className="bg-slate-50 sticky top-0 z-10 border-b">
                <tr className="text-left text-[11px] font-black uppercase text-slate-400 tracking-widest">
                  <th className="px-8 py-4 w-1/3">Name & Role</th>
                  <th className="px-8 py-4">Company</th>
                  <th className="px-8 py-4 text-right">Contact Info</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {contacts.map((contact) => (
                  <tr key={contact.id} className="group hover:bg-slate-50/50 transition-colors">
                    <td className="px-8 py-6">
                      <div className="flex items-center gap-4">
                        <div className="h-10 w-10 rounded-full bg-indigo-50 flex items-center justify-center text-indigo-600 border border-indigo-100">
                          <UserCircle className="h-6 w-6" />
                        </div>
                        <div>
                          <h4 className="font-bold text-slate-900 group-hover:text-indigo-600 transition-colors">{contact.name}</h4>
                          <p className="text-sm text-slate-500">{contact.jobTitle}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-8 py-6">
                      <div className="flex items-center gap-2">
                        <Building className="h-4 w-4 text-slate-400" />
                        <span className="font-semibold text-slate-700">{contact.companyName}</span>
                        <Badge variant="secondary" className="text-[10px] bg-slate-100">{contact.companyDomain}</Badge>
                      </div>
                    </td>
                    <td className="px-8 py-6 text-right">
                      {contact.unlocked ? (
                        <div className="flex items-center justify-end gap-3 animate-in fade-in slide-in-from-right-2">
                          <Button variant="outline" size="sm" className="h-9 px-3 border-emerald-200 text-emerald-700 bg-emerald-50 hover:bg-emerald-100">
                            <Mail className="h-4 w-4 mr-2" /> {contact.email}
                          </Button>
                          <Button variant="outline" size="sm" className="h-9 px-3 border-blue-200 text-blue-700 bg-blue-50 hover:bg-blue-100" onClick={() => window.open(contact.linkedinUrl, '_blank')}>
                            <Linkedin className="h-4 w-4" />
                          </Button>
                        </div>
                      ) : (
                        <Button 
                          size="sm" 
                          className="bg-indigo-600 hover:bg-indigo-700 h-9 font-bold group/btn"
                          onClick={() => handleUnlock(contact.id)}
                          disabled={isUnlocking === contact.id}
                        >
                          {isUnlocking === contact.id ? (
                            <LoadingSpinner size="sm" className="mr-2 text-white" />
                          ) : (
                            <Lock className="mr-2 h-4 w-4 group-hover/btn:scale-110 transition-transform" />
                          )}
                          Unlock Email
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="h-full flex flex-col items-center justify-center p-12 text-center">
            <div className="mb-8 relative">
               <div className="absolute -top-6 left-1/2 -translate-x-1/2 bg-indigo-600 text-white text-[10px] font-black uppercase px-3 py-1 rounded-full shadow-lg flex items-center gap-1">
                 <TrendingUp className="h-3 w-3" /> Beat 90% of candidates
               </div>
               <h2 className="text-4xl font-black text-slate-900 mb-2">Reach hiring managers before jobs are posted</h2>
               <p className="text-slate-500 text-lg">Up to 70% of jobs are never advertised. Here's how to land them:</p>
            </div>

            <div className="max-w-xl w-full space-y-8 mb-12">
               {[
                 { step: 1, text: "Enter the URL of a company you want to work for" },
                 { step: 2, text: "Find hiring managers and use credits to unlock their emails" },
                 { step: 3, text: "Email them your Resume/CV" }
               ].map(item => (
                 <div key={item.step} className="flex items-center gap-6 text-left group">
                   <div className="h-10 w-10 rounded-full bg-indigo-600 text-white flex items-center justify-center font-black text-lg shadow-md group-hover:scale-110 transition-transform shrink-0">
                     {item.step}
                   </div>
                   <p className="text-slate-700 font-bold text-lg">{item.text}</p>
                 </div>
               ))}
            </div>

            <Card className="max-w-2xl w-full bg-indigo-50/50 border-indigo-100 shadow-sm overflow-hidden">
               <CardContent className="p-8 text-left space-y-4">
                  <div className="flex items-center gap-2 text-indigo-900 font-bold">
                    <Building className="h-5 w-5" /> Example: "I want a Product Manager role at Stripe"
                  </div>
                  <div className="space-y-2 text-indigo-700 font-medium">
                    <div className="flex items-center gap-3">
                      <ChevronRight className="h-4 w-4 opacity-50" />
                      <span>Enter <span className="font-bold underline decoration-indigo-300">stripe.com</span></span>
                    </div>
                    <div className="flex items-center gap-3">
                      <ChevronRight className="h-4 w-4 opacity-50" />
                      <span>Function: <span className="font-bold">Product, Information Technology, Recruiting</span></span>
                    </div>
                    <div className="flex items-center gap-3">
                      <ChevronRight className="h-4 w-4 opacity-50" />
                      <span>Management Level: <span className="font-bold">VP, Head, Director, Manager</span></span>
                    </div>
                    <div className="flex items-center gap-3">
                      <ChevronRight className="h-4 w-4 opacity-50" />
                      <span>Unlock emails and contact hiring managers directly</span>
                    </div>
                  </div>
               </CardContent>
            </Card>
          </div>
        )}
      </main>
    </div>
  );
}
