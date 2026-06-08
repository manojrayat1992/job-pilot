import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '@/lib/api';
import { useAuthStore } from '@/store/authStore';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { useToast } from '@/hooks/use-toast';
import { Check, Sparkles, Zap, ShieldCheck } from 'lucide-react';

export default function Pricing() {
  const [upgrading, setUpgrading] = useState(false);
  const { user, setAuth } = useAuthStore();
  const { toast } = useToast();
  const navigate = useNavigate();

  const handleUpgrade = async () => {
    setUpgrading(true);
    try {
      const res = await api.post('/api/v1/billing/create-checkout-session');
      const { url } = res.data.data;
      
      if (url) {
        window.location.href = url;
      } else {
        throw new Error("No checkout URL returned");
      }
    } catch (error) {
      toast({
        title: 'Checkout Failed',
        description: 'Unable to start checkout session. Please try again.',
        variant: 'destructive'
      });
      setUpgrading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50/50 pb-20 relative overflow-hidden">
      {/* Background Gradients */}
      <div className="absolute top-0 left-0 w-full h-[500px] bg-gradient-to-b from-indigo-50 via-white to-transparent -z-10" />
      <div className="absolute top-[-20%] left-[-10%] w-[50%] h-[500px] bg-indigo-200/40 rounded-full blur-[120px] -z-10 mix-blend-multiply" />
      <div className="absolute top-[-10%] right-[-5%] w-[40%] h-[400px] bg-violet-200/30 rounded-full blur-[100px] -z-10 mix-blend-multiply" />

      <div className="max-w-6xl mx-auto px-4 pt-16">
        <div className="text-center space-y-4 mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-indigo-100/50 text-indigo-700 font-medium text-sm mb-4 border border-indigo-200/50">
            <Sparkles className="h-4 w-4" /> Unlock Your Full Potential
          </div>
          <h1 className="text-5xl md:text-6xl font-black tracking-tight text-slate-900">
            Upgrade to <span className="text-transparent bg-clip-text bg-gradient-to-r from-indigo-600 to-violet-600">Copilot Pro</span>
          </h1>
          <p className="text-xl text-slate-600 max-w-2xl mx-auto leading-relaxed">
            Stop worrying about credits. Get unlimited access to all premium AI agents and secure your dream job faster.
          </p>
        </div>

        <div className="grid md:grid-cols-2 gap-8 max-w-4xl mx-auto">
          {/* Free Tier */}
          <Card className="border-slate-200 shadow-sm rounded-[2rem] overflow-hidden bg-white/70 backdrop-blur-xl">
            <CardContent className="p-8 space-y-8">
              <div>
                <h3 className="text-2xl font-bold text-slate-900">Basic Plan</h3>
                <p className="text-slate-500 mt-2">Perfect for getting started</p>
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-5xl font-black text-slate-900">$0</span>
                <span className="text-slate-500 font-medium">/ forever</span>
              </div>
              <ul className="space-y-4">
                {[
                  '5 Free AI Credits per month',
                  'Basic Resume Builder',
                  'Standard Job Search',
                  '1 Mock Interview Session'
                ].map((feature, i) => (
                  <li key={i} className="flex items-center gap-3 text-slate-700">
                    <Check className="h-5 w-5 text-indigo-400" />
                    {feature}
                  </li>
                ))}
              </ul>
              <Button disabled variant="outline" className="w-full h-14 rounded-2xl font-bold text-lg border-2">
                {user?.role === 'PRO' ? 'Included' : 'Current Plan'}
              </Button>
            </CardContent>
          </Card>

          {/* Pro Tier */}
          <Card className="border-indigo-500 shadow-2xl shadow-indigo-500/10 rounded-[2rem] overflow-hidden bg-gradient-to-b from-indigo-600 to-violet-700 text-white relative">
            <div className="absolute top-0 right-0 p-4">
              <span className="bg-white/20 text-white text-xs font-bold px-3 py-1 rounded-full uppercase tracking-wider backdrop-blur-sm">
                Most Popular
              </span>
            </div>
            <CardContent className="p-8 space-y-8">
              <div>
                <h3 className="text-2xl font-bold text-white">Pro Plan</h3>
                <p className="text-indigo-200 mt-2">For serious job seekers</p>
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-5xl font-black text-white">₹2,499</span>
                <span className="text-indigo-200 font-medium">/ month</span>
              </div>
              <ul className="space-y-4">
                {[
                  'Unlimited AI Credits',
                  'Salary Negotiation Agent',
                  'Email Drip Sequence Generator',
                  'LinkedIn Profile Optimizer',
                  'Culture Fit Analysis',
                  'Unlimited Mock Interviews'
                ].map((feature, i) => (
                  <li key={i} className="flex items-center gap-3 text-indigo-50">
                    <Check className="h-5 w-5 text-indigo-300" />
                    {feature}
                  </li>
                ))}
              </ul>
              <Button 
                onClick={handleUpgrade}
                disabled={upgrading || user?.role === 'PRO'}
                className="w-full h-14 rounded-2xl font-bold text-lg bg-white text-indigo-600 hover:bg-slate-50 transition-colors"
              >
                {user?.role === 'PRO' ? 'You are PRO' : upgrading ? 'Processing...' : 'Upgrade Now'}
              </Button>
              <p className="text-center text-xs text-indigo-200 opacity-80 flex items-center justify-center gap-1">
                <ShieldCheck className="h-3 w-3" /> Supports Cards & UPI Payments
              </p>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
