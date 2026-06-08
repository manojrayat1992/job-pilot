import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '@/lib/api';
import { ResumeTemplate, ApiResponse } from '@/types';
import { Card, CardContent, CardHeader, CardTitle, CardDescription, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Badge } from '@/components/ui/badge';
import { 
  ArrowLeft, 
  Layout, 
  Check, 
  Sparkles, 
  User, 
  Mail, 
  Phone, 
  MapPin, 
  Briefcase, 
  GraduationCap, 
  Code,
  Save,
  Download,
  Trash2
} from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import { LoadingSpinner } from '@/components/loading/LoadingSpinner';

export default function ResumeBuilder() {
  const [step, setStep] = useState<'template' | 'content'>('template');
  const [templates, setTemplates] = useState<ResumeTemplate[]>([]);
  const [selectedTemplate, setSelectedTemplate] = useState<ResumeTemplate | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [resumeData, setResumeData] = useState<any>({
    title: 'My New Resume',
    personalInfo: {
      name: '',
      email: '',
      phone: '',
      location: '',
      summary: ''
    },
    experience: [],
    education: [],
    skills: []
  });

  const navigate = useNavigate();
  const { toast } = useToast();

  useEffect(() => {
    fetchTemplates();
  }, []);

  const fetchTemplates = async () => {
    try {
      const res = await api.get<ApiResponse<ResumeTemplate[]>>('/api/v1/resume-templates');
      setTemplates(res.data.data);
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to load templates.', variant: 'destructive' });
    } finally {
      setIsLoading(false);
    }
  };

  const handleTemplateSelect = (template: ResumeTemplate) => {
    setSelectedTemplate(template);
    setStep('content');
  };

  const handleSave = async () => {
    if (!selectedTemplate) return;
    setIsSaving(true);
    try {
      await api.post('/api/v1/resumes', {
        title: resumeData.title,
        parsedJson: {
          personalInfo: resumeData.personalInfo,
          experience: resumeData.experience,
          education: resumeData.education,
          skills: resumeData.skills,
          summary: resumeData.personalInfo.summary
        },
        templateId: selectedTemplate.id
      });
      toast({ title: 'Success', description: 'Resume created successfully!' });
      navigate('/resumes');
    } catch (error) {
      toast({ title: 'Error', description: 'Failed to save resume.', variant: 'destructive' });
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) return <LoadingSpinner size="lg" message="Loading designer tools..." className="min-h-screen" />;

  return (
    <div className="container mx-auto py-8 max-w-7xl">
      <div className="flex items-center gap-4 mb-8">
        <Button variant="ghost" onClick={() => step === 'content' ? setStep('template') : navigate('/resumes')}>
          <ArrowLeft className="mr-2 h-4 w-4" />
          Back
        </Button>
        <div>
          <h1 className="text-3xl font-bold tracking-tight">AI Resume Builder</h1>
          <p className="text-muted-foreground">
            {step === 'template' ? 'Choose a professional template to start' : 'Fill in your details to populate the template'}
          </p>
        </div>
      </div>

      {step === 'template' && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
          {templates.map((template) => (
            <Card 
              key={template.id} 
              className={`group overflow-hidden transition-all hover:ring-2 hover:ring-primary hover:shadow-xl cursor-pointer ${selectedTemplate?.id === template.id ? 'ring-2 ring-primary shadow-xl' : ''}`}
              onClick={() => handleTemplateSelect(template)}
            >
              <div className="aspect-[3/4] overflow-hidden relative">
                <img 
                  src={template.thumbnailUrl} 
                  alt={template.name} 
                  className="w-full h-full object-cover transition-transform group-hover:scale-105"
                />
                <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                  <Button variant="secondary" className="font-bold">
                    Use This Template
                  </Button>
                </div>
              </div>
              <CardHeader>
                <CardTitle>{template.name}</CardTitle>
                <CardDescription>{template.description}</CardDescription>
              </CardHeader>
            </Card>
          ))}
        </div>
      )}

      {step === 'content' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 h-[calc(100vh-200px)]">
          {/* Editor Panel */}
          <div className="overflow-y-auto pr-4 space-y-6">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Layout className="h-5 w-5" />
                  Resume Identity
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="resume-title">Resume Name (for your reference)</Label>
                  <Input 
                    id="resume-title" 
                    value={resumeData.title}
                    onChange={(e) => setResumeData({...resumeData, title: e.target.value})}
                  />
                </div>
              </CardContent>
            </Card>

            <Tabs defaultValue="personal" className="w-full">
              <TabsList className="grid w-full grid-cols-4">
                <TabsTrigger value="personal">Contact</TabsTrigger>
                <TabsTrigger value="experience">Experience</TabsTrigger>
                <TabsTrigger value="education">Education</TabsTrigger>
                <TabsTrigger value="skills">Skills</TabsTrigger>
              </TabsList>

              <TabsContent value="personal" className="space-y-6 pt-4">
                <Card>
                  <CardContent className="pt-6 space-y-4">
                    <div className="grid grid-cols-2 gap-4">
                      <div className="space-y-2">
                        <Label>Full Name</Label>
                        <Input 
                          value={resumeData.personalInfo.name}
                          onChange={(e) => setResumeData({
                            ...resumeData, 
                            personalInfo: {...resumeData.personalInfo, name: e.target.value}
                          })}
                          placeholder="John Doe"
                        />
                      </div>
                      <div className="space-y-2">
                        <Label>Email</Label>
                        <Input 
                          value={resumeData.personalInfo.email}
                          onChange={(e) => setResumeData({
                            ...resumeData, 
                            personalInfo: {...resumeData.personalInfo, email: e.target.value}
                          })}
                          placeholder="john@example.com"
                        />
                      </div>
                    </div>
                    <div className="grid grid-cols-2 gap-4">
                      <div className="space-y-2">
                        <Label>Phone</Label>
                        <Input 
                          value={resumeData.personalInfo.phone}
                          onChange={(e) => setResumeData({
                            ...resumeData, 
                            personalInfo: {...resumeData.personalInfo, phone: e.target.value}
                          })}
                          placeholder="+1 234 567 890"
                        />
                      </div>
                      <div className="space-y-2">
                        <Label>Location</Label>
                        <Input 
                          value={resumeData.personalInfo.location}
                          onChange={(e) => setResumeData({
                            ...resumeData, 
                            personalInfo: {...resumeData.personalInfo, location: e.target.value}
                          })}
                          placeholder="San Francisco, CA"
                        />
                      </div>
                    </div>
                    <div className="space-y-2">
                      <Label>Professional Summary</Label>
                      <textarea 
                        className="w-full min-h-[150px] p-3 rounded-md border border-input bg-background"
                        value={resumeData.personalInfo.summary}
                        onChange={(e) => setResumeData({
                          ...resumeData, 
                          personalInfo: {...resumeData.personalInfo, summary: e.target.value}
                        })}
                        placeholder="Experienced professional with expertise in..."
                      />
                    </div>
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="experience" className="space-y-6 pt-4">
                <div className="flex justify-between items-center">
                  <h3 className="text-lg font-semibold">Work Experience</h3>
                  <Button variant="outline" size="sm" onClick={() => {
                    const newExp = [...resumeData.experience, { company: '', title: '', location: '', bullets: [] }];
                    setResumeData({...resumeData, experience: newExp});
                  }}>
                    Add Experience
                  </Button>
                </div>
                {resumeData.experience.map((exp: any, idx: number) => (
                  <Card key={idx} className="relative group">
                    <CardContent className="pt-6 space-y-4">
                      <div className="flex justify-between items-center mb-2">
                        <span className="text-sm font-bold text-muted-foreground uppercase tracking-wider">Experience #{idx + 1}</span>
                        <Button 
                          variant="ghost" 
                          size="icon" 
                          className="h-8 w-8 text-destructive hover:bg-destructive/10 hover:text-destructive"
                          onClick={() => {
                            const newExp = resumeData.experience.filter((_: any, i: number) => i !== idx);
                            setResumeData({...resumeData, experience: newExp});
                          }}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                      <div className="grid grid-cols-2 gap-4">
                        <Input 
                          value={exp.company} 
                          onChange={(e) => {
                            const newExp = [...resumeData.experience];
                            newExp[idx].company = e.target.value;
                            setResumeData({...resumeData, experience: newExp});
                          }}
                          placeholder="Company" 
                        />
                        <Input 
                          value={exp.title} 
                          onChange={(e) => {
                            const newExp = [...resumeData.experience];
                            newExp[idx].title = e.target.value;
                            setResumeData({...resumeData, experience: newExp});
                          }}
                          placeholder="Job Title" 
                        />
                      </div>
                      <div className="grid grid-cols-2 gap-4">
                        <Input 
                          value={exp.location || ''} 
                          onChange={(e) => {
                            const newExp = [...resumeData.experience];
                            newExp[idx].location = e.target.value;
                            setResumeData({...resumeData, experience: newExp});
                          }}
                          placeholder="Location (e.g. San Francisco, CA)" 
                        />
                        <Input 
                          value={exp.dates || ''} 
                          onChange={(e) => {
                            const newExp = [...resumeData.experience];
                            newExp[idx].dates = e.target.value;
                            setResumeData({...resumeData, experience: newExp});
                          }}
                          placeholder="Dates (e.g. 2021 - Present)" 
                        />
                      </div>
                      <textarea 
                        className="w-full min-h-[100px] p-3 rounded-md border border-input bg-background text-sm"
                        placeholder="Bullet points (one per line)..."
                        value={exp.bullets?.join('\n') || ''}
                        onChange={(e) => {
                          const newExp = [...resumeData.experience];
                          newExp[idx].bullets = e.target.value.split('\n');
                          setResumeData({...resumeData, experience: newExp});
                        }}
                      />
                    </CardContent>
                  </Card>
                ))}
              </TabsContent>

              <TabsContent value="education" className="space-y-6 pt-4">
                <div className="flex justify-between items-center">
                  <h3 className="text-lg font-semibold">Education</h3>
                  <Button variant="outline" size="sm" onClick={() => {
                    const newEdu = [...resumeData.education, { institution: '', degree: '', field: '' }];
                    setResumeData({...resumeData, education: newEdu});
                  }}>
                    Add Education
                  </Button>
                </div>
                {resumeData.education.map((edu: any, idx: number) => (
                  <Card key={idx} className="relative group">
                    <CardContent className="pt-6 space-y-4">
                      <div className="flex justify-between items-center mb-2">
                        <span className="text-sm font-bold text-muted-foreground uppercase tracking-wider">Education #{idx + 1}</span>
                        <Button 
                          variant="ghost" 
                          size="icon" 
                          className="h-8 w-8 text-destructive hover:bg-destructive/10 hover:text-destructive"
                          onClick={() => {
                            const newEdu = resumeData.education.filter((_: any, i: number) => i !== idx);
                            setResumeData({...resumeData, education: newEdu});
                          }}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                      <Input 
                        value={edu.institution} 
                        onChange={(e) => {
                          const newEdu = [...resumeData.education];
                          newEdu[idx].institution = e.target.value;
                          setResumeData({...resumeData, education: newEdu});
                        }}
                        placeholder="Institution" 
                      />
                      <div className="grid grid-cols-2 gap-4">
                        <Input 
                          value={edu.degree} 
                          onChange={(e) => {
                            const newEdu = [...resumeData.education];
                            newEdu[idx].degree = e.target.value;
                            setResumeData({...resumeData, education: newEdu});
                          }}
                          placeholder="Degree (e.g. B.S.)" 
                        />
                        <Input 
                          value={edu.field} 
                          onChange={(e) => {
                            const newEdu = [...resumeData.education];
                            newEdu[idx].field = e.target.value;
                            setResumeData({...resumeData, education: newEdu});
                          }}
                          placeholder="Field of Study" 
                        />
                      </div>
                    </CardContent>
                  </Card>
                ))}
              </TabsContent>

              <TabsContent value="skills" className="space-y-6 pt-4">
                 <Card>
                  <CardContent className="pt-6 space-y-4">
                    <Label>Skills (comma separated)</Label>
                    <textarea 
                      className="w-full min-h-[150px] p-3 rounded-md border border-input bg-background"
                      placeholder="React, TypeScript, Node.js, AWS..."
                      onChange={(e) => setResumeData({
                        ...resumeData, 
                        skills: e.target.value.split(',').map(s => s.trim()).filter(s => s)
                      })}
                    />
                    <div className="flex flex-wrap gap-2">
                      {resumeData.skills.map((skill: string, i: number) => (
                        <Badge key={i} variant="secondary">{skill}</Badge>
                      ))}
                    </div>
                  </CardContent>
                </Card>
              </TabsContent>
            </Tabs>
          </div>

          {/* Preview Panel */}
          <div className="bg-slate-50 rounded-xl border-2 border-dashed border-slate-200 flex flex-col overflow-hidden">
            <div className="p-4 bg-white border-b flex justify-between items-center">
              <span className="text-sm font-bold text-slate-500 uppercase tracking-wider">Live Preview</span>
              <div className="flex gap-2">
                 <Button size="sm" variant="outline" onClick={() => toast({ title: 'Preview Export', description: 'Download feature coming soon!' })}>
                   <Download className="h-4 w-4 mr-2" /> Download
                 </Button>
                 <Button size="sm" onClick={handleSave} disabled={isSaving}>
                   <Save className="h-4 w-4 mr-2" /> {isSaving ? 'Saving...' : 'Finish Resume'}
                 </Button>
              </div>
            </div>
            
            <div className="flex-1 overflow-y-auto p-8 bg-white m-4 shadow-2xl rounded-sm">
              <div id="resume-preview-container" className="space-y-8">
                {/* Modern Professional Header */}
                <header className="border-b-2 border-primary pb-6">
                  <h2 className="text-4xl font-black text-slate-900 tracking-tight">{resumeData.personalInfo.name || 'Your Full Name'}</h2>
                  <div className="flex flex-wrap gap-x-4 gap-y-1 mt-3 text-slate-500 font-medium text-sm">
                    {resumeData.personalInfo.email && <span className="flex items-center gap-1"><Mail className="h-3 w-3" /> {resumeData.personalInfo.email}</span>}
                    {resumeData.personalInfo.phone && <span className="flex items-center gap-1"><Phone className="h-3 w-3" /> {resumeData.personalInfo.phone}</span>}
                    {resumeData.personalInfo.location && <span className="flex items-center gap-1"><MapPin className="h-3 w-3" /> {resumeData.personalInfo.location}</span>}
                  </div>
                </header>

                {/* Summary */}
                {resumeData.personalInfo.summary && (
                  <section>
                    <h3 className="text-xs font-black uppercase tracking-[0.2em] text-primary mb-3">Professional Summary</h3>
                    <p className="text-slate-600 leading-relaxed italic">"{resumeData.personalInfo.summary}"</p>
                  </section>
                )}

                {/* Experience */}
                <section>
                  <h3 className="text-xs font-black uppercase tracking-[0.2em] text-primary mb-6">Experience</h3>
                  <div className="space-y-6">
                    {resumeData.experience.length > 0 ? resumeData.experience.map((exp: any, i: number) => (
                      <div key={i} className="group relative">
                        <div className="flex justify-between items-start">
                          <div>
                            <h4 className="font-bold text-slate-900">{exp.title || 'Position'}</h4>
                            <p className="text-primary font-bold text-sm">{exp.company || 'Company Name'}</p>
                          </div>
                        </div>
                        <ul className="mt-3 space-y-2">
                          {exp.bullets.map((b: string, bi: number) => (
                            <li key={bi} className="text-sm text-slate-600 flex gap-2">
                              <span className="text-primary mt-1.5 h-1 w-1 rounded-full bg-primary shrink-0" />
                              {b}
                            </li>
                          ))}
                        </ul>
                      </div>
                    )) : (
                      <p className="text-sm text-slate-400 italic">Add your work history to see it here...</p>
                    )}
                  </div>
                </section>

                {/* Skills */}
                <section>
                  <h3 className="text-xs font-black uppercase tracking-[0.2em] text-primary mb-4">Core Competencies</h3>
                  <div className="flex flex-wrap gap-2">
                    {resumeData.skills.length > 0 ? resumeData.skills.map((skill: string, i: number) => (
                      <Badge key={i} variant="secondary" className="bg-slate-100 text-slate-700 border-none font-medium">
                        {skill}
                      </Badge>
                    )) : (
                      <p className="text-sm text-slate-400 italic">Add skills to showcase your expertise...</p>
                    )}
                  </div>
                </section>

                {/* Education */}
                <section>
                  <h3 className="text-xs font-black uppercase tracking-[0.2em] text-primary mb-4">Education</h3>
                  <div className="space-y-4">
                    {resumeData.education.length > 0 ? resumeData.education.map((edu: any, i: number) => (
                      <div key={i}>
                        <h4 className="font-bold text-slate-900">{edu.degree} {edu.field ? `in ${edu.field}` : ''}</h4>
                        <p className="text-sm text-slate-500 font-medium">{edu.institution}</p>
                      </div>
                    )) : (
                      <p className="text-sm text-slate-400 italic">Add your academic background...</p>
                    )}
                  </div>
                </section>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
