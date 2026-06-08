import React, { useEffect, useState } from 'react';
import api from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { PageLoader } from '@/components/loading/PageLoader';
import {
  Activity,
  Cpu,
  Database,
  Infinity as InfinityIcon,
  Zap,
  Clock,
  CheckCircle2,
  XCircle
} from 'lucide-react';
import { motion } from 'framer-motion';

export default function UsageDashboard() {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchUsage = async () => {
      try {
        const res = await api.get('/api/v1/ai/usage');
        setData(res.data.data);
      } catch (err) {
        console.error('Failed to fetch usage data', err);
      } finally {
        setLoading(false);
      }
    };
    fetchUsage();
  }, []);

  if (loading) return <PageLoader />;
  if (!data) return <div className="p-8 text-center text-slate-500">Failed to load usage data.</div>;

  const { credits, summary, modelStats, recentActivity } = data;

  return (
    <div className="space-y-8 animate-in fade-in zoom-in duration-500 max-w-6xl mx-auto py-8 px-4">
      
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-black text-slate-900 tracking-tight flex items-center gap-3">
          <Activity className="h-8 w-8 text-indigo-600" />
          Usage & AI Logs
        </h1>
      </div>

      {/* Top Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card className="bg-gradient-to-br from-indigo-500 to-violet-600 text-white border-none shadow-lg">
          <CardContent className="p-6">
            <div className="flex justify-between items-start mb-4">
              <div className="p-3 bg-white/20 rounded-xl">
                <Zap className="h-6 w-6" />
              </div>
              <Badge className="bg-white/20 hover:bg-white/30 text-white border-none">
                {credits.isUnlimited ? 'PRO PLAN' : 'FREE PLAN'}
              </Badge>
            </div>
            <p className="text-indigo-100 font-medium mb-1">Available AI Credits</p>
            <h2 className="text-5xl font-black">
              {credits.isUnlimited ? <InfinityIcon className="h-12 w-12" /> : credits.remaining}
            </h2>
            {!credits.isUnlimited && (
              <p className="text-sm text-indigo-100 mt-4 opacity-80">
                Upgrade to Pro for unlimited access to all AI features.
              </p>
            )}
          </CardContent>
        </Card>

        <Card className="shadow-sm border-slate-100">
          <CardContent className="p-6">
            <div className="p-3 bg-blue-50 text-blue-600 rounded-xl w-fit mb-4">
              <Database className="h-6 w-6" />
            </div>
            <p className="text-slate-500 font-medium mb-1">Total Tokens Processed</p>
            <h2 className="text-4xl font-black text-slate-900">
              {summary.totalTokens.toLocaleString()}
            </h2>
          </CardContent>
        </Card>

        <Card className="shadow-sm border-slate-100">
          <CardContent className="p-6">
            <div className="p-3 bg-emerald-50 text-emerald-600 rounded-xl w-fit mb-4">
              <Cpu className="h-6 w-6" />
            </div>
            <p className="text-slate-500 font-medium mb-1">Total AI Requests</p>
            <h2 className="text-4xl font-black text-slate-900">
              {summary.totalRequests.toLocaleString()}
            </h2>
          </CardContent>
        </Card>
      </div>

      {/* Model Usage Breakdown */}
      <Card className="shadow-sm border-slate-100">
        <CardHeader>
          <CardTitle className="text-lg font-bold">Usage by Model</CardTitle>
        </CardHeader>
        <CardContent>
          {modelStats.length === 0 ? (
            <p className="text-slate-500 text-sm">No model usage data available yet.</p>
          ) : (
            <div className="space-y-4">
              {modelStats.map((stat: any, idx: number) => {
                const percentage = summary.totalTokens > 0 
                  ? (stat.tokensUsed / summary.totalTokens) * 100 
                  : 0;
                return (
                  <div key={idx} className="space-y-2">
                    <div className="flex justify-between text-sm">
                      <span className="font-bold text-slate-700">{stat.model}</span>
                      <span className="text-slate-500">{stat.tokensUsed.toLocaleString()} tokens</span>
                    </div>
                    <div className="w-full bg-slate-100 rounded-full h-3">
                      <motion.div 
                        initial={{ width: 0 }}
                        animate={{ width: `${percentage}%` }}
                        transition={{ duration: 1, ease: "easeOut" }}
                        className="bg-indigo-500 h-3 rounded-full"
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Recent Activity Table */}
      <Card className="shadow-sm border-slate-100">
        <CardHeader>
          <CardTitle className="text-lg font-bold">Recent Activity</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {recentActivity.length === 0 ? (
            <div className="p-8 text-center text-slate-500">No recent AI activity found.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm text-left">
                <thead className="bg-slate-50 text-slate-500 uppercase text-xs font-bold border-y border-slate-100">
                  <tr>
                    <th className="px-6 py-4">Status</th>
                    <th className="px-6 py-4">Feature</th>
                    <th className="px-6 py-4">Model</th>
                    <th className="px-6 py-4">Tokens</th>
                    <th className="px-6 py-4">Duration</th>
                    <th className="px-6 py-4 text-right">Timestamp</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {recentActivity.map((log: any) => (
                    <tr key={log.id} className="hover:bg-slate-50/50 transition-colors">
                      <td className="px-6 py-4">
                        {log.status === 'SUCCESS' ? (
                          <CheckCircle2 className="h-5 w-5 text-emerald-500" />
                        ) : (
                          <XCircle className="h-5 w-5 text-rose-500" />
                        )}
                      </td>
                      <td className="px-6 py-4 font-medium text-slate-900">{log.feature}</td>
                      <td className="px-6 py-4 text-slate-600">
                        <Badge variant="outline" className="text-xs bg-white">{log.model}</Badge>
                      </td>
                      <td className="px-6 py-4 text-slate-600 font-mono text-xs">
                        {log.tokensUsed > 0 ? log.tokensUsed.toLocaleString() : '-'}
                      </td>
                      <td className="px-6 py-4 text-slate-500">
                        {log.durationMs > 0 ? `${(log.durationMs / 1000).toFixed(1)}s` : '-'}
                      </td>
                      <td className="px-6 py-4 text-right text-slate-500 whitespace-nowrap">
                        <div className="flex items-center justify-end gap-2">
                          <Clock className="h-3 w-3" />
                          {new Date(log.timestamp).toLocaleString()}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
