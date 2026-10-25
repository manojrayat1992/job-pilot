import { useEffect, useState } from 'react';
import axios from 'axios';
import api from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/textarea';
import type { Job, Resume } from '@/types';

export interface ResumeDraft {
  id: string;
  sourceResumeId: string;
  targetJobId: string | null;
  jobDescription: string;
  originalText: string;
  proposedText: string;
  approvedResumeId: string | null;
  approvedAt: string | null;
  createdAt: string;
}

function errorMessage(error: unknown) {
  return axios.isAxiosError(error) && typeof error.response?.data?.message === 'string'
    ? error.response.data.message : 'The request failed. Your original resume is unchanged. Please try again.';
}

export default function ResumeDraftDialog({ resume, initialDraftId, onClose }: {
  resume: Resume; initialDraftId?: string; onClose: () => void;
}) {
  const [drafts, setDrafts] = useState<ResumeDraft[]>([]);
  const [draft, setDraft] = useState<ResumeDraft | null>(null);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [jobId, setJobId] = useState('');
  const [jobDescription, setJobDescription] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const response = await api.get(`/api/v1/resumes/${resume.id}/drafts`);
        if (cancelled) return;
        const saved: ResumeDraft[] = response.data.data;
        setDrafts(saved);
        if (initialDraftId) {
          const selected = saved.find(item => item.id === initialDraftId);
          if (selected) setDraft(selected);
          else {
            const loaded = await api.get(`/api/v1/resume-drafts/${initialDraftId}`);
            if (!cancelled && loaded.data.data.sourceResumeId === resume.id) {
              setDraft(loaded.data.data); setDrafts(previous => [loaded.data.data, ...previous]);
            }
          }
        }
      } catch (err) { if (!cancelled) setError(errorMessage(err)); }
      finally { if (!cancelled) setLoading(false); }
    }
    void load();
    api.get('/api/v1/jobs').then(response => {
      if (!cancelled) setJobs(response.data.data.content);
    }).catch(() => { /* Pasted job descriptions remain available. */ });
    return () => { cancelled = true; };
  }, [resume.id, initialDraftId]);

  const generate = async () => {
    setBusy(true); setError(''); setConfirmed(false);
    try {
      const response = await api.post(`/api/v1/resumes/${resume.id}/drafts`, { jobId: jobId || null, jobDescription });
      const saved: ResumeDraft = response.data.data;
      setDraft(saved); setDrafts(previous => [saved, ...previous]);
    } catch (err) { setError(errorMessage(err)); }
    finally { setBusy(false); }
  };
  const approve = async () => {
    if (!draft || !confirmed) return;
    setBusy(true); setError('');
    try {
      const response = await api.post(`/api/v1/resume-drafts/${draft.id}/approve`, { confirmed: true });
      const saved: ResumeDraft = response.data.data;
      setDraft(saved); setDrafts(previous => previous.map(item => item.id === saved.id ? saved : item));
    } catch (err) { setError(errorMessage(err)); }
    finally { setBusy(false); }
  };
  const download = async () => {
    if (!draft) return;
    setBusy(true); setError('');
    try {
      const response = await api.get(`/api/v1/resume-drafts/${draft.id}/download`, { responseType: 'blob' });
      const url = URL.createObjectURL(response.data);
      const link = document.createElement('a');
      link.href = url; link.download = 'reviewed-resume.pdf';
      document.body.appendChild(link); link.click(); link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (err) { setError(errorMessage(err)); }
    finally { setBusy(false); }
  };

  const originalLines = new Set(draft?.originalText.split('\n'));
  return (
    <Dialog open onOpenChange={open => { if (!open && !busy) onClose(); }}>
      <DialogContent className="max-w-6xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Review a tailored version</DialogTitle>
          <DialogDescription>{resume.title} stays unchanged. Review the saved draft before creating and exporting a separate resume.</DialogDescription>
        </DialogHeader>
        {error && <p role="alert" className="text-destructive">{error}</p>}
        {loading ? <p role="status">Loading saved drafts…</p> : <>
          {drafts.length > 0 && <label className="grid gap-2 text-sm">Recent drafts (latest 50)
            <select className="border rounded p-2 bg-background" value={draft?.id || ''} disabled={busy}
              onChange={e => { setDraft(drafts.find(item => item.id === e.target.value) || null); setConfirmed(false); setError(''); }}>
              <option value="">Create a new draft</option>
              {drafts.map(item => <option key={item.id} value={item.id}>{new Date(item.createdAt).toLocaleString()} — {item.approvedAt ? 'Approved' : 'Needs review'}</option>)}
            </select>
          </label>}
          {!draft ? <div className="space-y-4">
            <label className="grid gap-2 text-sm">Target job
              <select className="border rounded p-2 bg-background" value={jobId} onChange={e => setJobId(e.target.value)} disabled={busy}>
                <option value="">Paste a job description</option>
                {jobs.map(job => <option key={job.id} value={job.id}>{job.title} — {job.company}</option>)}
              </select>
            </label>
            {!jobId && <label className="grid gap-2 text-sm">Job description
              <Textarea value={jobDescription} onChange={e => setJobDescription(e.target.value)} maxLength={30000} disabled={busy} className="min-h-40" />
            </label>}
            <Button disabled={busy || (!jobId && !jobDescription.trim())} onClick={generate}>{busy ? 'Preparing draft…' : 'Generate draft for review'}</Button>
          </div> : <div className="space-y-4">
            <details><summary className="cursor-pointer text-sm font-medium">Job description saved with this draft</summary><p className="whitespace-pre-wrap text-sm mt-2">{draft.jobDescription}</p></details>
            <p className="text-sm text-muted-foreground">New or rewritten lines are highlighted. Check every claim against your experience; automated checks cannot verify all wording.</p>
            <div className="grid md:grid-cols-2 gap-4">
              <section><h3 className="font-semibold mb-2">Original</h3><pre className="whitespace-pre-wrap break-words text-xs p-4 border rounded bg-muted/30">{draft.originalText}</pre></section>
              <section><h3 className="font-semibold mb-2">Proposed version</h3><div className="font-mono text-xs p-4 border rounded">
                {draft.proposedText.split('\n').map((line, index) => <div key={index} className={`whitespace-pre-wrap break-words ${originalLines.has(line) ? '' : 'bg-emerald-100 text-emerald-950'}`}>{line || '\u00a0'}</div>)}
              </div></section>
            </div>
            {draft.approvedAt ? <div className="space-y-3"><p role="status">Approved as a separate resume in your Resume Hub.</p><Button onClick={download} disabled={busy || !draft.approvedResumeId}>{busy ? 'Downloading…' : 'Download approved PDF'}</Button></div> : <>
              <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={confirmed} disabled={busy} onChange={e => setConfirmed(e.target.checked)} className="mt-1" />I reviewed the changes and confirm the claims accurately reflect my experience.</label>
              <Button onClick={approve} disabled={busy || !confirmed}>{busy ? 'Saving approved version…' : 'Approve and save separate resume'}</Button>
            </>}
            <Button variant="outline" onClick={() => { setDraft(null); setConfirmed(false); }} disabled={busy} className="ml-2">Create another draft</Button>
          </div>}
        </>}
        <div className="flex justify-end"><Button variant="outline" disabled={busy} onClick={onClose}>Close</Button></div>
      </DialogContent>
    </Dialog>
  );
}
