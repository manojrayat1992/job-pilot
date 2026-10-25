import ResumeDraftDialog from '@/components/resume/ResumeDraftDialog';
import { LoadingSpinner } from '@/components/loading/LoadingSpinner';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card,CardContent,CardDescription,CardHeader,CardTitle } from '@/components/ui/card';
import {
Dialog,
DialogContent,
DialogDescription,
DialogFooter,
DialogHeader,
DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs,TabsContent,TabsList,TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from '@/hooks/use-toast';
import { useApiCall } from '@/hooks/useApiCall';
import api from '@/lib/api';
import { Resume } from '@/types';
import { Briefcase,CheckCircle2,Code,Download,Edit3,Eye,FileText,GraduationCap,Layout,Mail,Save,Sparkles,Trash2,Upload,User } from 'lucide-react';
import { useEffect,useRef,useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

export default function Resumes() {
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [uploading, setUploading] = useState(false);
  const [editingResume, setEditingResume] = useState<Resume | null>(null);
  const [viewingResume, setViewingResume] = useState<Resume | null>(null);
  const [editedJson, setEditedJson] = useState<string>('');
  const [skillsText, setSkillsText] = useState<string>('');
  const [isSaving, setIsSaving] = useState(false);
  const [tailoringResume, setTailoringResume] = useState<Resume | null>(null);
  const [searchParams, setSearchParams] = useSearchParams();
  const draftSourceId = searchParams.get('source');
  const [linkedSource, setLinkedSource] = useState<Resume | null>(null);
  const initialDraftId = searchParams.get('draft') || undefined;

  const fileInputRef = useRef<HTMLInputElement>(null);
  const { toast } = useToast();
  const { execute, isLoading } = useApiCall();
  const navigate = useNavigate();

  useEffect(() => {
    fetchResumes();
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLinkedSource(null);
    if (draftSourceId) {
      api.get(`/api/v1/resumes/${draftSourceId}`).then(response => {
        if (!cancelled) setLinkedSource(response.data.data);
      }).catch(() => { if (!cancelled) toast({ title: 'Resume unavailable', description: 'This resume may have been deleted.', variant: 'destructive' }); });
    }
    return () => { cancelled = true; };
  }, [draftSourceId, toast]);

  const fetchResumes = async () => {
    await execute(
      async () => {
        const res = await api.get('/api/v1/resumes');
        setResumes(res.data.data.content);
      },
      { errorMessage: 'Failed to load resumes. Please try again.' }
    );
  };

  const handleEditClick = (resume: Resume) => {
    setEditingResume(resume);
    setEditedJson(JSON.stringify(resume.parsedJson || {}, null, 2));
    setSkillsText((resume.parsedJson?.skills || []).join(', '));
  };

  const handleSave = async () => {
    if (!editingResume) return;
    setIsSaving(true);
    try {
      // Sync skills text back to the JSON before saving
      const finalSkills = skillsText.split(',').map(s => s.trim()).filter(s => s !== '');
      const parsed = JSON.parse(editedJson);
      parsed.skills = finalSkills;

      await api.put(`/api/v1/resumes/${editingResume.id}`, {
        parsedJson: parsed,
        title: editingResume.title,
        tags: editingResume.tags
      });
      toast({ title: 'Success', description: 'Saved a separate version. Your original resume is unchanged.' });
      setEditingResume(null);
      fetchResumes();
    } catch (error) {
      console.error('Save failed', error);
      toast({ 
        title: 'Save Failed', 
        description: error instanceof SyntaxError ? 'Invalid JSON format.' : 'Failed to update resume.', 
        variant: 'destructive' 
      });
    } finally {
      setIsSaving(false);
    }
  };

  const handleCreateNew = async () => {
    try {
      const res = await api.post('/api/v1/resumes', { title: 'New Resume' });
      toast({ title: 'Created', description: 'New blank resume created.' });
      handleEditClick(res.data.data);
      fetchResumes();
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to create resume.', variant: 'destructive' });
    }
  };

  const handleUploadClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'].includes(file.type)) {
      toast({ title: 'Invalid file type', description: 'Please upload a PDF or DOCX file.', variant: 'destructive' });
      return;
    }

    setUploading(true);
    const formData = new FormData();
    formData.append('file', file);
    formData.append('title', file.name.split('.')[0]);

    try {
      const response = await api.post('/api/v1/resumes/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      toast({ title: 'Success', description: 'Resume uploaded and parsed successfully.' });
      
      // Automatically open the editor for the newly uploaded resume
      if (response.data.data) {
        handleEditClick(response.data.data);
      }
      
      fetchResumes();
    } catch (error) {
      console.error('Upload failed', error);
      toast({ title: 'Upload Failed', description: 'Failed to process resume.', variant: 'destructive' });
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const activateResume = async (id: string) => {
    try {
      await api.put(`/api/v1/resumes/${id}/activate`);
      fetchResumes();
      toast({ title: 'Activated', description: 'Resume set as active.' });
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to activate resume.', variant: 'destructive' });
    }
  };

  const deleteResume = async (id: string) => {
    if (!confirm('Are you sure you want to delete this resume?')) return;
    try {
      await api.delete(`/api/v1/resumes/${id}`);
      fetchResumes();
      toast({ title: 'Deleted', description: 'Resume removed.' });
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to delete resume.', variant: 'destructive' });
    }
  };

  const downloadPdf = async (id: string) => {
    try {
      const response = await api.get(`/api/v1/resumes/${id}/download`, { responseType: 'blob' });
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'resume.pdf');
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to download PDF.', variant: 'destructive' });
    }
  };

  if (isLoading) return <LoadingSpinner size="md" message="Loading resumes..." className="py-8" />;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Resume Hub</h2>
          <p className="text-muted-foreground">Manage, parse, and activate your resumes.</p>
        </div>
        <input 
          type="file" 
          ref={fileInputRef} 
          className="hidden" 
          accept=".pdf,.docx" 
          onChange={handleFileChange} 
        />
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => navigate('/resumes/builder')}>
            <Layout className="mr-2 h-4 w-4" />
            Create From Template
          </Button>
          <Button variant="outline" onClick={handleCreateNew}>
            <Edit3 className="mr-2 h-4 w-4" />
            Create From Scratch
          </Button>
          <Button onClick={handleUploadClick} disabled={uploading}>
            <Upload className="mr-2 h-4 w-4" />
            {uploading ? 'Processing...' : 'Upload Resume'}
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {resumes.map((resume) => (
          <Card key={resume.id} className={`relative transition-all hover:shadow-md ${resume.active ? 'border-primary ring-1 ring-primary' : ''}`}>
            {resume.active && (
              <div className="absolute -top-3 -right-3 bg-primary text-primary-foreground rounded-full p-1 shadow-sm z-10">
                <CheckCircle2 className="h-5 w-5" />
              </div>
            )}
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-xl">
                <FileText className="h-5 w-5 text-muted-foreground" />
                <span className="truncate">{resume.title}</span>
              </CardTitle>
              <CardDescription>
                Uploaded {new Date(resume.createdAt).toLocaleDateString()}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                <div className="flex flex-wrap gap-2">
                  <Badge variant="outline">v{resume.version}</Badge>
                  {resume.parentResumeId && <Badge variant="secondary">Separate version</Badge>}
                  {resume.atsScore !== undefined && (
                    <Badge className={resume.atsScore >= 80 ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : resume.atsScore >= 60 ? 'bg-amber-50 text-amber-700 border-amber-200' : 'bg-rose-50 text-rose-700 border-rose-200'}>
                      ATS: {resume.atsScore}
                    </Badge>
                  )}
                  {resume.tags?.map((tag, i) => (
                    <Badge key={i} variant="secondary">{tag}</Badge>
                  ))}
                </div>
                
                <div className="flex justify-between items-center pt-4 border-t">
                  <div className="space-x-2">
                    <Button variant="outline" size="sm" onClick={() => setViewingResume(resume)} title="View Resume">
                      <Eye className="h-4 w-4" />
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => handleEditClick(resume)} title="Edit Parsed Info">
                      <Edit3 className="h-4 w-4" />
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => downloadPdf(resume.id)} title="Download PDF">
                      <Download className="h-4 w-4" />
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => setTailoringResume(resume)} title="Tailor and review versions" className="text-indigo-600 border-indigo-200 hover:bg-indigo-50">
                      <Sparkles className="h-4 w-4" />
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => deleteResume(resume.id)} className="text-destructive hover:bg-destructive/10">
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                  {!resume.active && (
                    <Button variant="secondary" size="sm" onClick={() => activateResume(resume.id)}>
                      Set Active
                    </Button>
                  )}
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
        {resumes.length === 0 && !uploading && (
           <div className="col-span-full text-center py-12 border-2 border-dashed rounded-lg border-muted">
             <FileText className="mx-auto h-12 w-12 text-muted-foreground mb-4 opacity-50" />
             <h3 className="text-lg font-medium text-muted-foreground">No resumes found</h3>
             <p className="text-sm text-muted-foreground mt-1 mb-4">Upload a PDF or DOCX file to get started.</p>
             <Button onClick={handleUploadClick} variant="outline">Upload First Resume</Button>
           </div>
        )}
      </div>

      {/* Resume Editor Dialog */}
      <Dialog open={!!editingResume} onOpenChange={(open) => !open && setEditingResume(null)}>
        <DialogContent className="max-w-4xl max-h-[90vh] flex flex-col">
          <DialogHeader>
            <DialogTitle>Edit Extracted Information</DialogTitle>
            <DialogDescription>
              Review and refine the information extracted from your resume. Saving creates a separate version and preserves this original.
            </DialogDescription>
          </DialogHeader>
          
          <div className="flex-1 overflow-y-auto py-4">
            <Tabs 
              defaultValue="basic" 
              className="w-full"
              onValueChange={(value) => {
                if (value !== 'json') {
                  try {
                    const parsed = JSON.parse(editedJson);
                    if (parsed.skills && Array.isArray(parsed.skills)) {
                      setSkillsText(parsed.skills.join(', '));
                    }
                    setEditingResume(prev => prev ? { ...prev, parsedJson: parsed } : null);
                  } catch (e) {
                    // Ignore syntax errors while typing
                  }
                } else {
                  const finalSkills = skillsText.split(',').map(s => s.trim()).filter(s => s !== '');
                  const currentJson = {
                    ...(editingResume?.parsedJson || {}),
                    skills: finalSkills
                  };
                  setEditedJson(JSON.stringify(currentJson, null, 2));
                }
              }}
            >
              <TabsList className="grid w-full grid-cols-5">
                <TabsTrigger value="basic">Personal</TabsTrigger>
                <TabsTrigger value="experience">Experience</TabsTrigger>
                <TabsTrigger value="education">Education</TabsTrigger>
                <TabsTrigger value="skills">Skills</TabsTrigger>
                <TabsTrigger value="json">JSON</TabsTrigger>
              </TabsList>
              
              <TabsContent value="basic" className="space-y-4 pt-4">
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="name">Full Name</Label>
                    <Input 
                      id="name" 
                      value={editingResume?.parsedJson?.personalInfo?.name || ''} 
                      onChange={(e) => {
                        const newJson = {...(editingResume?.parsedJson || {})};
                        newJson.personalInfo = {...(newJson.personalInfo || {}), name: e.target.value};
                        setEditingResume({...editingResume!, parsedJson: newJson});
                        setEditedJson(JSON.stringify(newJson, null, 2));
                      }}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="email">Email</Label>
                    <Input 
                      id="email" 
                      value={editingResume?.parsedJson?.personalInfo?.email || ''} 
                      onChange={(e) => {
                        const newJson = {...(editingResume?.parsedJson || {})};
                        newJson.personalInfo = {...(newJson.personalInfo || {}), email: e.target.value};
                        setEditingResume({...editingResume!, parsedJson: newJson});
                        setEditedJson(JSON.stringify(newJson, null, 2));
                      }}
                    />
                  </div>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="summary">Professional Summary</Label>
                  <Textarea 
                    id="summary" 
                    className="min-h-[100px]"
                    value={editingResume?.parsedJson?.summary || ''} 
                    onChange={(e) => {
                      const newJson = {...(editingResume?.parsedJson || {}), summary: e.target.value};
                      setEditingResume({...editingResume!, parsedJson: newJson});
                      setEditedJson(JSON.stringify(newJson, null, 2));
                    }}
                  />
                </div>
              </TabsContent>
              
              <TabsContent value="experience" className="pt-4">
                <div className="space-y-4">
                  <div className="flex justify-between items-center">
                    <Label className="text-base font-bold">Work Experience</Label>
                    <Button variant="outline" size="sm" onClick={() => {
                      const newJson = {...(editingResume?.parsedJson || {})};
                      newJson.experience = [...(newJson.experience || []), { company: '', title: '', location: '', dates: '', bullets: [] }];
                      setEditingResume({...editingResume!, parsedJson: newJson});
                      setEditedJson(JSON.stringify(newJson, null, 2));
                    }}>
                      Add Experience
                    </Button>
                  </div>
                  {editingResume?.parsedJson?.experience?.map((exp: any, idx: number) => (
                    <div key={idx} className="p-4 border rounded-lg space-y-3 relative group bg-card">
                      <div className="flex justify-between items-center">
                        <span className="text-sm font-bold text-muted-foreground uppercase tracking-wider">Experience #{idx + 1}</span>
                        <Button 
                          variant="ghost" 
                          size="icon" 
                          className="h-8 w-8 text-destructive hover:bg-destructive/10 hover:text-destructive"
                          onClick={() => {
                            const newJson = {...(editingResume?.parsedJson || {})};
                            newJson.experience = (newJson.experience || []).filter((_: any, i: number) => i !== idx);
                            setEditingResume({...editingResume!, parsedJson: newJson});
                            setEditedJson(JSON.stringify(newJson, null, 2));
                          }}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                      <div className="grid grid-cols-2 gap-3">
                        <div className="space-y-1">
                          <span className="text-xs font-semibold text-muted-foreground">Company</span>
                          <Input 
                            value={exp.company || ''} 
                            placeholder="Company" 
                            onChange={(e) => {
                              const newJson = {...(editingResume?.parsedJson || {})};
                              newJson.experience = [...(newJson.experience || [])];
                              newJson.experience[idx] = { ...newJson.experience[idx], company: e.target.value };
                              setEditingResume({...editingResume!, parsedJson: newJson});
                              setEditedJson(JSON.stringify(newJson, null, 2));
                            }}
                          />
                        </div>
                        <div className="space-y-1">
                          <span className="text-xs font-semibold text-muted-foreground">Job Title</span>
                          <Input 
                            value={exp.title || ''} 
                            placeholder="Job Title" 
                            onChange={(e) => {
                              const newJson = {...(editingResume?.parsedJson || {})};
                              newJson.experience = [...(newJson.experience || [])];
                              newJson.experience[idx] = { ...newJson.experience[idx], title: e.target.value };
                              setEditingResume({...editingResume!, parsedJson: newJson});
                              setEditedJson(JSON.stringify(newJson, null, 2));
                            }}
                          />
                        </div>
                      </div>
                      <div className="grid grid-cols-2 gap-3">
                        <div className="space-y-1">
                          <span className="text-xs font-semibold text-muted-foreground">Location</span>
                          <Input 
                            value={exp.location || ''} 
                            placeholder="Location (e.g. San Francisco, CA)" 
                            onChange={(e) => {
                              const newJson = {...(editingResume?.parsedJson || {})};
                              newJson.experience = [...(newJson.experience || [])];
                              newJson.experience[idx] = { ...newJson.experience[idx], location: e.target.value };
                              setEditingResume({...editingResume!, parsedJson: newJson});
                              setEditedJson(JSON.stringify(newJson, null, 2));
                            }}
                          />
                        </div>
                        <div className="space-y-1">
                          <span className="text-xs font-semibold text-muted-foreground">Dates</span>
                          <Input 
                            value={exp.dates || exp.duration || ''} 
                            placeholder="Dates (e.g. 2021 - Present)" 
                            onChange={(e) => {
                              const newJson = {...(editingResume?.parsedJson || {})};
                              newJson.experience = [...(newJson.experience || [])];
                              newJson.experience[idx] = { ...newJson.experience[idx], dates: e.target.value, duration: e.target.value };
                              setEditingResume({...editingResume!, parsedJson: newJson});
                              setEditedJson(JSON.stringify(newJson, null, 2));
                            }}
                          />
                        </div>
                      </div>
                      <div className="space-y-1">
                        <span className="text-xs font-semibold text-muted-foreground">Description Bullets (one per line)</span>
                        <Textarea 
                          className="w-full min-h-[100px] font-sans text-sm"
                          placeholder="Bullet points (one per line)..."
                          value={exp.bullets?.join('\n') || ''}
                          onChange={(e) => {
                            const newJson = {...(editingResume?.parsedJson || {})};
                            newJson.experience = [...(newJson.experience || [])];
                            newJson.experience[idx] = { 
                              ...newJson.experience[idx], 
                              bullets: e.target.value.split('\n')
                            };
                            setEditingResume({...editingResume!, parsedJson: newJson});
                            setEditedJson(JSON.stringify(newJson, null, 2));
                          }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </TabsContent>

              <TabsContent value="education" className="pt-4">
                <div className="space-y-4">
                  <div className="flex justify-between items-center">
                    <Label className="text-base font-bold">Education History</Label>
                    <Button variant="outline" size="sm" onClick={() => {
                      const newJson = {...(editingResume?.parsedJson || {})};
                      newJson.education = [...(newJson.education || []), { institution: '', degree: '', field: '', dates: '' }];
                      setEditingResume({...editingResume!, parsedJson: newJson});
                      setEditedJson(JSON.stringify(newJson, null, 2));
                    }}>
                      Add Education
                    </Button>
                  </div>
                  {editingResume?.parsedJson?.education?.map((edu: any, idx: number) => (
                    <div key={idx} className="p-4 border rounded-lg space-y-3 relative group bg-card">
                      <div className="flex justify-between items-center">
                        <span className="text-sm font-bold text-muted-foreground uppercase tracking-wider">Education #{idx + 1}</span>
                        <Button 
                          variant="ghost" 
                          size="icon" 
                          className="h-8 w-8 text-destructive hover:bg-destructive/10 hover:text-destructive"
                          onClick={() => {
                            const newJson = {...(editingResume?.parsedJson || {})};
                            newJson.education = (newJson.education || []).filter((_: any, i: number) => i !== idx);
                            setEditingResume({...editingResume!, parsedJson: newJson});
                            setEditedJson(JSON.stringify(newJson, null, 2));
                          }}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                      <div className="space-y-1">
                        <span className="text-xs font-semibold text-muted-foreground">Institution</span>
                        <Input 
                          value={edu.institution || ''} 
                          placeholder="Institution" 
                          onChange={(e) => {
                            const newJson = {...(editingResume?.parsedJson || {})};
                            newJson.education = [...(newJson.education || [])];
                            newJson.education[idx] = { ...newJson.education[idx], institution: e.target.value };
                            setEditingResume({...editingResume!, parsedJson: newJson});
                            setEditedJson(JSON.stringify(newJson, null, 2));
                          }}
                        />
                      </div>
                      <div className="grid grid-cols-3 gap-3">
                        <div className="space-y-1">
                          <span className="text-xs font-semibold text-muted-foreground">Degree</span>
                          <Input 
                            value={edu.degree || ''} 
                            placeholder="Degree" 
                            onChange={(e) => {
                              const newJson = {...(editingResume?.parsedJson || {})};
                              newJson.education = [...(newJson.education || [])];
                              newJson.education[idx] = { ...newJson.education[idx], degree: e.target.value };
                              setEditingResume({...editingResume!, parsedJson: newJson});
                              setEditedJson(JSON.stringify(newJson, null, 2));
                            }}
                          />
                        </div>
                        <div className="space-y-1">
                          <span className="text-xs font-semibold text-muted-foreground">Field of Study</span>
                          <Input 
                            value={edu.field || ''} 
                            placeholder="Field of Study" 
                            onChange={(e) => {
                              const newJson = {...(editingResume?.parsedJson || {})};
                              newJson.education = [...(newJson.education || [])];
                              newJson.education[idx] = { ...newJson.education[idx], field: e.target.value };
                              setEditingResume({...editingResume!, parsedJson: newJson});
                              setEditedJson(JSON.stringify(newJson, null, 2));
                            }}
                          />
                        </div>
                        <div className="space-y-1">
                          <span className="text-xs font-semibold text-muted-foreground">Dates</span>
                          <Input 
                            value={edu.dates || edu.duration || ''} 
                            placeholder="Dates" 
                            onChange={(e) => {
                              const newJson = {...(editingResume?.parsedJson || {})};
                              newJson.education = [...(newJson.education || [])];
                              newJson.education[idx] = { ...newJson.education[idx], dates: e.target.value, duration: e.target.value };
                              setEditingResume({...editingResume!, parsedJson: newJson});
                              setEditedJson(JSON.stringify(newJson, null, 2));
                            }}
                          />
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </TabsContent>

              <TabsContent value="skills" className="pt-4">
                <div className="space-y-4">
                  <Label htmlFor="skills-input">Skills (comma separated)</Label>
                  <Textarea 
                    id="skills-input" 
                    className="min-h-[200px]"
                    placeholder="Java, Spring Boot, React..."
                    value={skillsText} 
                    onChange={(e) => setSkillsText(e.target.value)}
                  />
                  <div className="flex flex-wrap gap-2 pt-2">
                    {skillsText.split(',')
                      .map(s => s.trim())
                      .filter(s => s !== '')
                      .map((skill: string, i: number) => (
                        <Badge key={i} variant="secondary">{skill}</Badge>
                    ))}
                  </div>
                </div>
              </TabsContent>
              
              <TabsContent value="json" className="pt-4 h-full">
                <div className="space-y-2 h-full">
                  <Label htmlFor="json-editor">Structured Data (JSON)</Label>
                  <Textarea 
                    id="json-editor" 
                    className="font-mono text-xs min-h-[400px] h-full"
                    value={editedJson}
                    onChange={(e) => setEditedJson(e.target.value)}
                  />
                </div>
              </TabsContent>
            </Tabs>
          </div>
          
          <DialogFooter className="mt-4 border-t pt-4">
            <Button variant="outline" onClick={() => setEditingResume(null)}>Cancel</Button>
            <Button onClick={handleSave} disabled={isSaving}>
              {isSaving ? 'Saving...' : (
                <>
                  <Save className="mr-2 h-4 w-4" />
                  Save New Version
                </>
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {(tailoringResume || linkedSource) && (
        <ResumeDraftDialog
          key={tailoringResume?.id || draftSourceId}
          resume={(tailoringResume || linkedSource)!}
          initialDraftId={tailoringResume ? undefined : initialDraftId}
          onClose={() => { setTailoringResume(null); setSearchParams({}); void fetchResumes(); }}
        />
      )}

      {/* Resume Viewer Dialog */}
      <Dialog open={!!viewingResume} onOpenChange={(open) => !open && setViewingResume(null)}>
        <DialogContent className="max-w-4xl max-h-[90vh] flex flex-col overflow-hidden p-0 gap-0 border-none shadow-2xl">
          <div className="bg-primary/5 p-8 border-b border-primary/10">
            <div className="flex justify-between items-start">
              <div className="space-y-1">
                <h2 className="text-3xl font-black text-slate-900">{viewingResume?.parsedJson?.personalInfo?.name || viewingResume?.title}</h2>
                <div className="flex items-center gap-4 text-slate-500 font-medium">
                  {viewingResume?.parsedJson?.personalInfo?.email && (
                    <span className="flex items-center gap-1.5"><Mail className="h-4 w-4" /> {viewingResume.parsedJson.personalInfo.email}</span>
                  )}
                  {viewingResume?.parsedJson?.personalInfo?.phone && (
                    <span className="flex items-center gap-1.5">📞 {viewingResume.parsedJson.personalInfo.phone}</span>
                  )}
                </div>
              </div>
              <div className="flex gap-2">
                <Button variant="outline" className="bg-white rounded-xl h-10 px-4 font-bold" onClick={() => downloadPdf(viewingResume!.id)}>
                   <Download className="h-4 w-4 mr-2" /> PDF
                </Button>
                <Button variant="secondary" className="rounded-xl h-10 px-4 font-bold" onClick={() => setViewingResume(null)}>
                   Close
                </Button>
              </div>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-8 space-y-10 custom-scrollbar bg-white">
            {/* Summary */}
            {viewingResume?.parsedJson?.summary && (
              <section className="space-y-4">
                <h3 className="text-xs font-black uppercase tracking-[0.2em] text-primary flex items-center gap-2">
                  <User className="h-4 w-4" /> Professional Profile
                </h3>
                <p className="text-slate-600 leading-relaxed text-lg font-serif italic">
                  "{viewingResume.parsedJson.summary}"
                </p>
              </section>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-12">
              <div className="lg:col-span-2 space-y-10">
                {/* Experience */}
                <section className="space-y-6">
                  <h3 className="text-xs font-black uppercase tracking-[0.2em] text-primary flex items-center gap-2">
                    <Briefcase className="h-4 w-4" /> Experience
                  </h3>
                  <div className="space-y-8">
                    {viewingResume?.parsedJson?.experience?.map((exp: any, idx: number) => (
                      <div key={idx} className="relative pl-6 before:absolute before:left-0 before:top-2 before:bottom-0 before:w-px before:bg-slate-100">
                        <div className="absolute left-[-4px] top-2 h-2 w-2 rounded-full bg-primary" />
                        <div className="flex justify-between items-start mb-2">
                          <div>
                            <h4 className="font-bold text-slate-900">{exp.title}</h4>
                            <p className="text-primary font-bold text-sm">{exp.company}</p>
                          </div>
                          <span className="text-xs font-black text-slate-400 uppercase">{exp.location}</span>
                        </div>
                        <ul className="space-y-2 mt-4">
                          {exp.bullets?.map((bullet: string, bi: number) => (
                            <li key={bi} className="text-sm text-slate-600 flex gap-3">
                              <span className="text-primary mt-1.5 h-1 w-1 rounded-full bg-primary shrink-0" />
                              {bullet}
                            </li>
                          ))}
                        </ul>
                      </div>
                    ))}
                  </div>
                </section>
              </div>

              <div className="space-y-10">
                {/* Skills */}
                <section className="space-y-4">
                  <h3 className="text-xs font-black uppercase tracking-[0.2em] text-primary flex items-center gap-2">
                    <Code className="h-4 w-4" /> Core Skills
                  </h3>
                  <div className="flex flex-wrap gap-2">
                    {viewingResume?.parsedJson?.skills?.map((skill: string, i: number) => (
                      <Badge key={i} variant="secondary" className="bg-slate-100 text-slate-700 hover:bg-primary/10 hover:text-primary transition-colors border-none py-1.5 px-3">
                        {skill}
                      </Badge>
                    ))}
                  </div>
                </section>

                {/* Education */}
                <section className="space-y-4">
                  <h3 className="text-xs font-black uppercase tracking-[0.2em] text-primary flex items-center gap-2">
                    <GraduationCap className="h-4 w-4" /> Education
                  </h3>
                  <div className="space-y-4">
                    {viewingResume?.parsedJson?.education?.map((edu: any, i: number) => (
                      <div key={i} className="space-y-1">
                        <h4 className="font-bold text-sm text-slate-900">{edu.degree}</h4>
                        <p className="text-xs text-slate-500">{edu.institution}</p>
                      </div>
                    ))}
                  </div>
                </section>
              </div>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>

  );
}
