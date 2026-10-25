import { LoadingSpinner } from '@/components/loading/LoadingSpinner';
import { Button } from '@/components/ui/button';
import { Card,CardContent,CardFooter } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useToast } from '@/hooks/use-toast';
import api from '@/lib/api';
import { useAuthStore } from '@/store/authStore';
import {
Briefcase,
Building,
ChevronLeft,
ChevronRight,
DollarSign,
MapPin,
Rocket,
ShieldCheck,
Sparkles,
Zap
} from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function Onboarding() {
  const [step, setStep] = useState(1);
  const [isLoading, setIsLoading] = useState(false);
  const { user, setUser } = useAuthStore();
  const navigate = useNavigate();
  const { toast } = useToast();

  const [formData, setFormData] = useState({
    workMode: '',
    targetRole: '',
    currentOrg: '',
    targetSalary: ''
  });

  const handleNext = () => setStep(prev => prev + 1);
  const handleBack = () => setStep(prev => prev - 1);

  const handleSubmit = async () => {
    if (!user) {
      navigate('/login');
      return;
    }
    setIsLoading(true);
    try {
      const response = await api.post('/api/v1/users/onboarding', formData);
      const updatedUser = response.data.data;
      
      // Update store with new user info
      setUser({
        ...user,
        onboardingCompleted: true,
        workMode: updatedUser.workMode,
        targetRole: updatedUser.targetRole,
        currentOrg: updatedUser.currentOrg,
        targetSalary: updatedUser.targetSalary,
        credits: updatedUser.credits
      });

      toast({ 
        title: 'Welcome aboard!', 
        description: 'Your career profile is ready. Let\'s find your dream job.' 
      });
      navigate('/dashboard');
    } catch (error: any) {
      console.error('Onboarding Error:', error.response?.data || error.message);
      toast({ 
        title: 'Submission Failed', 
        description: error.response?.data?.message || 'Could not save your preferences. Please try again.', 
        variant: 'destructive' 
      });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col items-center justify-center p-4">
      {/* Background decoration */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] rounded-full bg-primary/5 blur-3xl" />
        <div className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] rounded-full bg-primary/5 blur-3xl" />
      </div>

      <div className="max-w-xl w-full space-y-8 relative z-10">
        <div className="text-center space-y-2">
          <div className="inline-flex items-center justify-center h-16 w-16 rounded-2xl bg-primary text-white shadow-xl shadow-primary/20 mb-4 animate-bounce">
            <Rocket className="h-8 w-8" />
          </div>
          <h1 className="text-4xl font-black text-slate-900 tracking-tight">Personalize Your Journey</h1>
          <p className="text-slate-500 font-medium">Complete your profile to unlock all AI capabilities.</p>
        </div>

        <Card className="border-none shadow-2xl rounded-3xl overflow-hidden">
          <div className="h-2 bg-slate-100 w-full overflow-hidden">
            <div 
              className="h-full bg-primary transition-all duration-500 ease-out" 
              style={{ width: `${(step / 4) * 100}%` }}
            />
          </div>

          <CardContent className="p-10">
            {step === 1 && (
              <div className="space-y-6 animate-in fade-in slide-in-from-right-4">
                <div className="space-y-2">
                  <h2 className="text-2xl font-bold flex items-center gap-2">
                    <MapPin className="text-primary h-6 w-6" />
                    How do you prefer to work?
                  </h2>
                  <p className="text-slate-500">We'll prioritize jobs that match your lifestyle.</p>
                </div>
                <div className="grid grid-cols-1 gap-4">
                  {['Remote', 'Hybrid', 'On-site'].map((mode) => (
                    <div 
                      key={mode}
                      onClick={() => setFormData({ ...formData, workMode: mode })}
                      className={`p-6 rounded-2xl border-2 transition-all cursor-pointer flex items-center justify-between group ${formData.workMode === mode ? 'border-primary bg-primary/5 shadow-md' : 'border-slate-100 hover:border-primary/40'}`}
                    >
                      <span className={`text-lg font-bold ${formData.workMode === mode ? 'text-primary' : 'text-slate-600'}`}>{mode}</span>
                      <div className={`h-6 w-6 rounded-full border-2 flex items-center justify-center transition-all ${formData.workMode === mode ? 'bg-primary border-primary scale-110' : 'border-slate-200 group-hover:border-primary/40'}`}>
                        {formData.workMode === mode && <ShieldCheck className="h-4 w-4 text-white" />}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {step === 2 && (
              <div className="space-y-6 animate-in fade-in slide-in-from-right-4">
                <div className="space-y-2">
                  <h2 className="text-2xl font-bold flex items-center gap-2">
                    <Briefcase className="text-primary h-6 w-6" />
                    What is your target role?
                  </h2>
                  <p className="text-slate-500">Our AI will use this to find the best career matches.</p>
                </div>
                <div className="space-y-4">
                  <Label htmlFor="role">Job Title / Designation</Label>
                  <Input 
                    id="role"
                    placeholder="e.g. Senior Software Engineer"
                    className="h-14 text-lg px-4 rounded-xl border-slate-200 focus:ring-primary/20"
                    value={formData.targetRole}
                    onChange={(e) => setFormData({ ...formData, targetRole: e.target.value })}
                  />
                  <div className="flex flex-wrap gap-2 pt-2">
                    {['Product Manager', 'UX Designer', 'Frontend Dev', 'Data Analyst'].map(suggestion => (
                      <button 
                        key={suggestion}
                        onClick={() => setFormData({ ...formData, targetRole: suggestion })}
                        className="px-3 py-1.5 rounded-lg bg-slate-100 text-slate-600 text-sm font-bold hover:bg-primary/10 hover:text-primary transition-colors"
                      >
                        {suggestion}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            )}

            {step === 3 && (
              <div className="space-y-6 animate-in fade-in slide-in-from-right-4">
                <div className="space-y-2">
                  <h2 className="text-2xl font-bold flex items-center gap-2">
                    <Building className="text-primary h-6 w-6" />
                    Where are you working now?
                  </h2>
                  <p className="text-slate-500">Help us understand your current career stage.</p>
                </div>
                <div className="space-y-4">
                  <Label htmlFor="org">Current Company / Organization</Label>
                  <Input 
                    id="org"
                    placeholder="e.g. Google or Freelance"
                    className="h-14 text-lg px-4 rounded-xl border-slate-200 focus:ring-primary/20"
                    value={formData.currentOrg}
                    onChange={(e) => setFormData({ ...formData, currentOrg: e.target.value })}
                  />
                </div>
              </div>
            )}

            {step === 4 && (
              <div className="space-y-6 animate-in fade-in slide-in-from-right-4">
                <div className="space-y-2">
                  <h2 className="text-2xl font-bold flex items-center gap-2">
                    <DollarSign className="text-primary h-6 w-6" />
                    What is your target salary?
                  </h2>
                  <p className="text-slate-500">We'll use this to filter opportunities that match your expectations.</p>
                </div>
                <div className="space-y-4">
                  <Label htmlFor="salary">Expected Annual Salary Range</Label>
                  <Input 
                    id="salary"
                    placeholder="e.g. $120k - $150k"
                    className="h-14 text-lg px-4 rounded-xl border-slate-200 focus:ring-primary/20"
                    value={formData.targetSalary}
                    onChange={(e) => setFormData({ ...formData, targetSalary: e.target.value })}
                  />
                  <div className="p-4 bg-amber-50 rounded-xl border border-amber-100 flex gap-3 text-amber-700 text-sm leading-relaxed">
                    <Zap className="h-5 w-5 shrink-0" />
                    <span>Tip: Providing an expected salary helps our AI prioritize higher-paying roles in your research reports.</span>
                  </div>
                </div>
              </div>
            )}
          </CardContent>

          <CardFooter className="p-10 pt-0 flex justify-between bg-slate-50/50">
            {step > 1 ? (
              <Button variant="ghost" onClick={handleBack} disabled={isLoading} className="font-bold">
                <ChevronLeft className="mr-2 h-4 w-4" />
                Back
              </Button>
            ) : (
              <div />
            )}
            
            {step < 4 ? (
              <Button 
                onClick={handleNext} 
                disabled={!isStepValid()}
                className="px-8 h-12 rounded-xl font-bold bg-primary hover:bg-primary/90 shadow-lg shadow-primary/20"
              >
                Next Step
                <ChevronRight className="ml-2 h-4 w-4" />
              </Button>
            ) : (
              <Button 
                onClick={handleSubmit} 
                disabled={isLoading || !isStepValid()}
                className="px-8 h-12 rounded-xl font-bold bg-emerald-600 hover:bg-emerald-700 shadow-lg shadow-emerald-200 text-white"
              >
                {isLoading ? <LoadingSpinner size="sm" className="mr-2 text-white" /> : <Sparkles className="mr-2 h-4 w-4" />}
                Complete Setup
              </Button>
            )}
          </CardFooter>
        </Card>
      </div>
    </div>
  );

  function isStepValid() {
    if (step === 1) return !!formData.workMode;
    if (step === 2) return !!formData.targetRole;
    if (step === 3) return true; // Optional
    if (step === 4) return !!formData.targetSalary;
    return true;
  }
}
