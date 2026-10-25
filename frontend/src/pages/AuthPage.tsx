import { Button } from '@/components/ui/button';
import { FormInput } from '@/components/ui/FormInput';
import { useToast } from '@/hooks/use-toast';
import { useAuthStore } from '@/store/authStore';
import { zodResolver } from '@hookform/resolvers/zod';
import { AnimatePresence,motion,useMotionValue,useSpring } from 'framer-motion';
import {
Activity,
ArrowLeft,
ArrowRight,
Cpu,
Lock,
Mail,
Rocket,
Settings,
Shield,
ShieldCheck,
Sparkles,
User,
Zap
} from 'lucide-react';
import React,{ useEffect,useState } from 'react';
import { useForm } from 'react-hook-form';
import { useLocation,useNavigate } from 'react-router-dom';
import * as z from 'zod';

const loginSchema = z.object({
  email: z.string().email('Invalid email address'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
});

const registerSchema = z.object({
  name: z.string().min(2, 'Name is required'),
  email: z.string().email('Invalid email address'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
  confirmPassword: z.string(),
  terms: z.boolean().refine(val => val === true, {
    message: 'You must accept the terms',
  }),
}).refine((data) => data.password === data.confirmPassword, {
  message: "Passwords don't match",
  path: ["confirmPassword"],
});

type LoginData = z.infer<typeof loginSchema>;
type RegisterData = z.infer<typeof registerSchema>;

const ACTIVITY_FEED = [
  "Global Scout: Identified 12 roles at Netflix",
  "Intelligence Grid: AI Tech Lead persona ready",
  "Semantic Match: Profile aligned with 98% precision",
  "Market Analysis: Cloud Engineering demand up 14%",
  "Automation: Application dispatched to Stripe",
  "Global Scout: High-match opportunity found at OpenAI",
  "Intelligence Grid: Mock interview report finalized",
  "Semantic Match: Profile keywords optimized",
  "Market Analysis: Hybrid work trends increasing",
  "Automation: Cover letter generated for Anthropic"
];

const TAGLINES = [
  "Your Career, Accelerated.",
  "Your Vision, Orchestrated.",
  "Your Ambition, Automated.",
  "Your Future, Architected."
];

export default function AuthPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { login, register: signUp } = useAuthStore();
  const { toast } = useToast();
  
  const [isLogin, setIsLogin] = useState(location.pathname === '/login');
  const [isLoading, setIsLoading] = useState(false);
  const [taglineIndex, setTaglineIndex] = useState(0);
  const [bootProgress, setBootProgress] = useState(0);
  const [isBooted, setIsBooted] = useState(false);

  // Parallax Mouse Effect
  const mouseX = useMotionValue(0);
  const mouseY = useMotionValue(0);
  const springX = useSpring(mouseX, { damping: 40, stiffness: 200 });
  const springY = useSpring(mouseY, { damping: 40, stiffness: 200 });
  
  // Spotlight Cursor Follower
  const spotlightX = useSpring(mouseX, { damping: 50, stiffness: 300 });
  const spotlightY = useSpring(mouseY, { damping: 50, stiffness: 300 });

  useEffect(() => {
    // Tagline cycler
    const taglineInterval = setInterval(() => {
      setTaglineIndex((prev) => (prev + 1) % TAGLINES.length);
    }, 3500);

    // Boot Sequence simulation
    const bootInterval = setInterval(() => {
      setBootProgress((prev) => {
        if (prev >= 100) {
          clearInterval(bootInterval);
          setTimeout(() => setIsBooted(true), 500);
          return 100;
        }
        return prev + Math.random() * 15;
      });
    }, 100);

    return () => {
      clearInterval(taglineInterval);
      clearInterval(bootInterval);
    };
  }, []);

  const handleMouseMove = (e: React.MouseEvent) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    mouseX.set(x - rect.width / 2);
    mouseY.set(y - rect.height / 2);
  };

  const loginForm = useForm<LoginData>({
    resolver: zodResolver(loginSchema),
  });

  const registerForm = useForm<RegisterData>({
    resolver: zodResolver(registerSchema),
  });

  const onLoginSubmit = async (data: LoginData) => {
    setIsLoading(true);
    try {
      await login(data.email, data.password);
      toast({ title: "Welcome back!", description: "Successfully logged in." });
      navigate('/dashboard');
    } catch (error: any) {
      const message = error.response?.data?.message || "Please check your credentials.";
      toast({ 
        title: "Login failed", 
        description: message, 
        variant: "destructive" 
      });
    } finally {
      setIsLoading(false);
    }
  };

  const onRegisterSubmit = async (data: RegisterData) => {
    setIsLoading(true);
    try {
      await signUp(data.name, data.email, data.password);
      toast({ title: "Account created!", description: "Welcome to Job Pilot ." });
      navigate('/dashboard');
    } catch (error: any) {
      const message = error.response?.data?.message || "An error occurred during sign up.";
      toast({ 
        title: "Registration failed", 
        description: message, 
        variant: "destructive" 
      });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-white flex overflow-hidden font-sans select-none relative" onMouseMove={handleMouseMove}>
      
      {/* INITIALIZATION OVERLAY */}
      <AnimatePresence>
        {!isBooted && (
          <motion.div 
            exit={{ opacity: 0, scale: 1.05 }}
            className="fixed inset-0 z-[100] bg-[#020617] flex flex-col items-center justify-center p-10"
          >
            <div className="w-full max-w-md space-y-8">
               <div className="flex justify-between items-end mb-2">
                  <div className="flex items-center gap-3 text-indigo-500">
                    <Rocket className="h-6 w-6 animate-pulse" />
                    <span className="text-[11px] font-black uppercase tracking-[0.4em]">Engine_Secure_Boot</span>
                  </div>
                  <span className="text-2xl font-black text-white font-mono">{Math.floor(bootProgress)}%</span>
               </div>
               <div className="h-1.5 w-full bg-white/5 rounded-full overflow-hidden border border-white/10">
                  <motion.div 
                    initial={{ width: 0 }}
                    animate={{ width: `${bootProgress}%` }}
                    className="h-full bg-indigo-500 shadow-[0_0_30px_rgba(79,70,229,0.9)]"
                  />
               </div>
               <div className="grid grid-cols-2 gap-y-4 gap-x-8 text-[9px] font-black text-slate-500 uppercase tracking-widest pt-6">
                  <div className="flex items-center gap-2">
                    <div className="h-1 w-1 rounded-full bg-emerald-500" /> Identity_Shield: OK
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="h-1 w-1 rounded-full bg-emerald-500" /> Vector_Lattice: READY
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="h-1 w-1 rounded-full bg-indigo-500 animate-pulse" /> Career_Scout: ACTIVE
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="h-1 w-1 rounded-full bg-emerald-500" /> Encryption: AES_256
                  </div>
               </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* LEFT SIDE: BRAND / VISUAL (42%) */}
      <div className="hidden lg:flex lg:w-[42%] bg-[#020617] relative overflow-hidden flex-col justify-between p-16 border-r border-white/10">
        
        {/* Spotlight Effect */}
        <motion.div 
          style={{ 
            x: spotlightX, 
            y: spotlightY,
            background: 'radial-gradient(circle, rgba(79,70,229,0.12) 0%, transparent 70%)'
          }}
          className="absolute -left-[300px] -top-[300px] w-[600px] h-[600px] pointer-events-none z-20"
        />

        {/* Dynamic Backgrounds */}
        <div className="absolute inset-0 z-0">
          <motion.div 
            style={{ x: springX, y: springY }}
            className="absolute top-1/4 left-1/4 w-[600px] h-[600px] bg-indigo-600/5 blur-[120px] rounded-full" 
          />
          <div className="absolute inset-0 bg-[url('https://grainy-gradients.vercel.app/noise.svg')] opacity-30 pointer-events-none" />
          <div className="absolute inset-0 opacity-[0.03] bg-[linear-gradient(to_right,#80808012_1px,transparent_1px),linear-gradient(to_bottom,#80808012_1px,transparent_1px)] bg-[size:50px_50px]" />
        </div>

        {/* Live System Activity */}
        <div className="absolute right-0 top-0 bottom-0 w-60 overflow-hidden pointer-events-none opacity-25 hidden xl:block z-20">
          <motion.div 
            animate={{ y: [0, -1000] }}
            transition={{ duration: 50, repeat: Infinity, ease: "linear" }}
            className="flex flex-col gap-14 py-10"
          >
            {[...ACTIVITY_FEED, ...ACTIVITY_FEED, ...ACTIVITY_FEED].map((text, i) => (
              <div key={i} className="flex flex-col gap-3">
                <div className="flex items-center gap-2 text-[9px] font-black text-indigo-400 uppercase tracking-[0.3em]">
                  <Activity className="h-3.5 w-3.5" /> Intelligence_Stream
                </div>
                <div className="text-[10px] font-mono text-white/50 leading-relaxed uppercase tracking-tighter w-48 border-l border-indigo-500/30 pl-4 italic">
                  {text}
                </div>
              </div>
            ))}
          </motion.div>
          <div className="absolute inset-0 bg-gradient-to-b from-[#020617] via-transparent to-[#020617]" />
        </div>

        {/* Brand Identity */}
        <div className="relative z-30 space-y-12">
          <motion.div 
            whileHover={{ scale: 1.05 }}
            className="flex items-center gap-3 cursor-pointer group w-fit"
            onClick={() => navigate('/')}
          >
            <div className="h-12 w-12 bg-indigo-600 rounded-2xl flex items-center justify-center shadow-[0_0_40px_rgba(79,70,229,0.5)] transition-transform group-hover:rotate-6">
              <Rocket className="text-white h-7 w-7" />
            </div>
            <div className="flex flex-col">
               <span className="text-2xl font-black tracking-tighter uppercase text-white leading-none">Job Pilot </span>
               <span className="text-[10px] font-black text-indigo-500 uppercase tracking-[0.5em]">Command Hub</span>
            </div>
          </motion.div>

          <div className="space-y-8 pt-10">
            <div className="h-40 flex flex-col justify-center">
              <AnimatePresence mode="wait">
                <motion.h1 
                  key={taglineIndex}
                  initial={{ opacity: 0, y: 30, rotateX: 90 }}
                  animate={{ opacity: 1, y: 0, rotateX: 0 }}
                  exit={{ opacity: 0, y: -30, rotateX: -90 }}
                  transition={{ duration: 0.9, ease: [0.19, 1, 0.22, 1] }}
                  className="text-7xl font-black tracking-tighter leading-[0.85] text-white italic uppercase"
                >
                  {TAGLINES[taglineIndex].split(',')[0]} <br />
                  <span className="text-transparent bg-clip-text bg-gradient-to-r from-indigo-500 to-indigo-300">
                    {TAGLINES[taglineIndex].split(',')[1]}
                  </span>
                </motion.h1>
              </AnimatePresence>
            </div>
            <p className="text-slate-500 text-xl max-w-sm leading-relaxed font-semibold italic">
              Your career is no longer a manual process. Deploy intelligence.
            </p>
          </div>
        </div>

        {/* Performance Metrics Card */}
        <motion.div 
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 1 }}
          className="relative z-30 space-y-10 bg-white/[0.02] border border-white/5 p-10 rounded-[44px] backdrop-blur-md group"
        >
          <div className="absolute top-0 right-0 p-8 opacity-10 group-hover:opacity-30 transition-opacity">
             <Settings className="h-24 w-24 text-white animate-spin-slow" />
          </div>
          <div className="grid grid-cols-1 gap-8">
             <FeatureItem icon={<Shield className="text-indigo-500" />} text="Identity Shield" sub="End-to-End Vault Encryption" />
             <FeatureItem icon={<Cpu className="text-indigo-500" />} text="Intelligence Grid" sub="pgvector High-Precision Match" />
             <FeatureItem icon={<Zap className="text-indigo-500" />} text="Application Auto-Pilot" sub="Autonomous Application Flows" />
          </div>
          <div className="pt-8 border-t border-white/5 flex items-center justify-between">
            <div className="text-[10px] font-black uppercase tracking-[0.4em] text-slate-500">Node_Status: Encrypted</div>
            <div className="flex gap-1.5">
              {[1,2,3].map(i => <div key={i} className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" />)}
            </div>
          </div>
        </motion.div>
      </div>

      {/* RIGHT SIDE: INTERFACE (58%) */}
      <div className="flex-1 flex flex-col justify-center items-center p-8 md:p-20 bg-white relative overflow-y-auto">
        <div className="absolute top-10 left-10 lg:hidden">
           <Button variant="ghost" onClick={() => navigate('/')} className="gap-2 font-black text-slate-400 uppercase text-[10px] tracking-widest">
             <ArrowLeft className="h-4 w-4" /> Exit_Terminal
           </Button>
        </div>

        <motion.div 
          layout
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="w-full max-w-md space-y-12"
        >
          <div className="space-y-4">
            <div className="inline-flex items-center gap-3 px-4 py-2 rounded-full bg-indigo-50 border border-indigo-100 text-indigo-600 text-[11px] font-black uppercase tracking-[0.3em]">
              {isLogin ? <ShieldCheck className="h-4 w-4" /> : <Sparkles className="h-4 w-4" />}
              {isLogin ? "Verified_Access" : "Initialize_Session"}
            </div>
            <h2 className="text-5xl font-black text-slate-900 uppercase tracking-tighter italic leading-none">
              {isLogin ? "Welcome Back" : "Create Account"}
            </h2>
            <p className="text-slate-500 font-semibold text-lg leading-relaxed italic">
              {isLogin ? "Resume your autonomous career journey." : "Begin your high-performance trial period."}
            </p>
          </div>

          <AnimatePresence mode="wait">
            {isLogin ? (
              <motion.form 
                key="login"
                initial={{ opacity: 0, x: 10 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -10 }}
                onSubmit={loginForm.handleSubmit(onLoginSubmit)}
                className="space-y-8"
              >
                <FormInput 
                  {...loginForm.register('email')}
                  label="Work Identity" 
                  icon={Mail} 
                  placeholder="name@professional.com"
                  error={loginForm.formState.errors.email?.message}
                />
                <div className="space-y-4">
                  <FormInput 
                    {...loginForm.register('password')}
                    label="Security Passphrase" 
                    type="password"
                    icon={Lock} 
                    placeholder="••••••••"
                    error={loginForm.formState.errors.password?.message}
                  />
                  <div className="flex justify-end">
                    <button type="button" className="text-[11px] font-black uppercase text-indigo-600 hover:text-indigo-800 tracking-widest flex items-center gap-2 group italic">
                      Recover Passphrase <ArrowRight className="h-3.5 w-3.5 group-hover:translate-x-1 transition-transform" />
                    </button>
                  </div>
                </div>
                <motion.div whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}>
                  <Button 
                    type="submit" 
                    disabled={isLoading}
                    className="w-full h-16 rounded-[28px] bg-indigo-600 hover:bg-indigo-700 text-white font-black uppercase tracking-[0.3em] shadow-2xl shadow-indigo-100 transition-all flex items-center justify-center gap-3"
                  >
                    {isLoading ? "Validating..." : "Resume Session"} <Zap className="h-4 w-4 fill-current" />
                  </Button>
                </motion.div>
              </motion.form>
            ) : (
              <motion.form 
                key="register"
                initial={{ opacity: 0, x: 10 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -10 }}
                onSubmit={registerForm.handleSubmit(onRegisterSubmit)}
                className="space-y-6"
              >
                <FormInput 
                  {...registerForm.register('name')}
                  label="Professional Name" 
                  icon={User} 
                  placeholder="Full Name"
                  error={registerForm.formState.errors.name?.message}
                />
                <FormInput 
                  {...registerForm.register('email')}
                  label="Work Email" 
                  icon={Mail} 
                  placeholder="name@professional.com"
                  error={registerForm.formState.errors.email?.message}
                />
                <div className="grid grid-cols-2 gap-4">
                  <FormInput 
                    {...registerForm.register('password')}
                    label="Security Key" 
                    type="password"
                    icon={Lock} 
                    placeholder="••••••••"
                    error={registerForm.formState.errors.password?.message}
                  />
                  <FormInput 
                    {...registerForm.register('confirmPassword')}
                    label="Verify Key" 
                    type="password"
                    icon={ShieldCheck} 
                    placeholder="••••••••"
                    error={registerForm.formState.errors.confirmPassword?.message}
                  />
                </div>
                
                <div className="p-4 rounded-3xl bg-slate-50 border border-slate-100 flex items-start gap-4">
                  <input 
                    type="checkbox" 
                    id="terms" 
                    {...registerForm.register('terms')}
                    className="mt-1 h-4.5 w-4.5 rounded-lg border-slate-300 text-indigo-600 focus:ring-indigo-600"
                  />
                  <label htmlFor="terms" className="text-[11px] font-bold text-slate-500 leading-snug uppercase tracking-widest italic">
                    Accept <span className="text-indigo-600 underline">Terms of Service</span> and <span className="text-indigo-600 underline">Privacy Shield</span>.
                  </label>
                </div>
                {registerForm.formState.errors.terms && (
                  <p className="text-[10px] text-red-500 font-black uppercase ml-2 tracking-tighter">{registerForm.formState.errors.terms.message}</p>
                )}

                <motion.div whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}>
                  <Button 
                    type="submit" 
                    disabled={isLoading}
                    className="w-full h-16 rounded-[28px] bg-indigo-600 hover:bg-indigo-700 text-white font-black uppercase tracking-[0.3em] shadow-2xl shadow-indigo-100 transition-all flex items-center justify-center gap-3"
                  >
                    {isLoading ? "Provisioning..." : "Start Trial"} <Rocket className="h-4 w-4 fill-current" />
                  </Button>
                </motion.div>
              </motion.form>
            )}
          </AnimatePresence>

          <div className="text-center pt-8 border-t border-slate-100">
             <p className="text-sm font-semibold text-slate-400 italic">
               {isLogin ? "New to the platform?" : "Already an engineer?"}
               <button 
                onClick={() => setIsLogin(!isLogin)}
                className="ml-3 font-black text-indigo-600 hover:text-indigo-800 uppercase tracking-[0.3em] text-[11px] border-b-2 border-indigo-100 hover:border-indigo-600 transition-all pb-1"
               >
                 {isLogin ? "Join Now" : "Sign In"}
               </button>
             </p>
          </div>
        </motion.div>
      </div>

      <style>{`
        @keyframes spin-slow {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
        .animate-spin-slow {
          animation: spin-slow 15s linear infinite;
        }
      `}</style>
    </div>
  );
}

function FeatureItem({ icon, text, sub }: { icon: React.ReactNode, text: string, sub: string }) {
  return (
    <div className="flex items-center gap-6 text-white group cursor-default">
      <div className="h-14 w-14 rounded-2xl bg-white/[0.04] border border-white/5 flex items-center justify-center transition-all duration-500 group-hover:bg-indigo-600/30 group-hover:border-indigo-600/50 group-hover:scale-110 shadow-inner">
        {icon}
      </div>
      <div className="flex flex-col">
        <span className="text-[13px] font-black uppercase tracking-[0.3em] leading-none mb-2">{text}</span>
        <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest italic">{sub}</span>
      </div>
    </div>
  );
}
