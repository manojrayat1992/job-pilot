import { useState, useEffect } from 'react';
import api from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { ArrowLeft, Plus, Trash2, Save, Settings, Zap } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import { useNavigate } from 'react-router-dom';

interface ModelVersion {
  id: string;
  versionName: string;
  modelPath: string;
  isActive: boolean;
  trafficWeight: number;
  createdAt: string;
}

export default function ModelManagement() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const [models, setModels] = useState<ModelVersion[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [newModel, setNewModel] = useState({ versionName: '', modelPath: '', trafficWeight: 0 });

  useEffect(() => {
    fetchModels();
  }, []);

  const fetchModels = async () => {
    try {
      const res = await api.get('/api/v1/ai/admin/models');
      setModels(res.data.data);
    } catch (error) {
      toast({ title: 'Failed to load models', variant: 'destructive' });
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async () => {
    if (!newModel.versionName || !newModel.modelPath) {
      toast({ title: 'Missing fields', description: 'Version name and model path are required.', variant: 'destructive' });
      return;
    }
    try {
      await api.post('/api/v1/ai/admin/models', newModel);
      toast({ title: 'Model created successfully' });
      setShowCreateForm(false);
      setNewModel({ versionName: '', modelPath: '', trafficWeight: 0 });
      fetchModels();
    } catch (error) {
      toast({ title: 'Failed to create model', variant: 'destructive' });
    }
  };

  const handleToggleActive = async (id: string, isActive: boolean) => {
    try {
      await api.put(`/api/v1/ai/admin/models/${id}`, { isActive: !isActive });
      toast({ title: 'Model updated successfully' });
      fetchModels();
    } catch (error) {
      toast({ title: 'Failed to update model', variant: 'destructive' });
    }
  };

  const handleUpdateWeight = async (id: string, weight: number) => {
    try {
      await api.put(`/api/v1/ai/admin/models/${id}`, { trafficWeight: weight });
      toast({ title: 'Model updated successfully' });
      fetchModels();
    } catch (error) {
      toast({ title: 'Failed to update model', variant: 'destructive' });
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Are you sure you want to delete this model version?')) return;
    try {
      await api.delete(`/api/v1/ai/admin/models/${id}`);
      toast({ title: 'Model deleted successfully' });
      fetchModels();
    } catch (error) {
      toast({ title: 'Failed to delete model', variant: 'destructive' });
    }
  };

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
                <Settings className="h-5 w-5 text-indigo-600" /> Model Management
              </h1>
              <p className="text-xs text-slate-400 font-medium">AI Model Version Control & A/B Testing</p>
            </div>
          </div>
          <Button onClick={() => setShowCreateForm(!showCreateForm)} className="gap-2">
            <Plus className="h-4 w-4" /> Add Model
          </Button>
        </div>
      </div>

      <div className="container mx-auto px-4 py-8 max-w-6xl">
        {showCreateForm && (
          <Card className="mb-8 border-indigo-200 shadow-sm">
            <CardContent className="p-6">
              <h3 className="text-lg font-bold text-slate-900 mb-4">Create New Model Version</h3>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="space-y-2">
                  <label className="text-sm font-bold text-slate-700">Version Name</label>
                  <Input
                    placeholder="e.g., gpt-4o-mini-v2"
                    value={newModel.versionName}
                    onChange={e => setNewModel({ ...newModel, versionName: e.target.value })}
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-bold text-slate-700">Model Path</label>
                  <Input
                    placeholder="e.g., gpt-4o-mini"
                    value={newModel.modelPath}
                    onChange={e => setNewModel({ ...newModel, modelPath: e.target.value })}
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-bold text-slate-700">Traffic Weight (0-100)</label>
                  <Input
                    type="number"
                    min="0"
                    max="100"
                    placeholder="0"
                    value={newModel.trafficWeight}
                    onChange={e => setNewModel({ ...newModel, trafficWeight: parseInt(e.target.value) || 0 })}
                  />
                </div>
              </div>
              <div className="flex gap-3 mt-4">
                <Button onClick={handleCreate} className="gap-2">
                  <Save className="h-4 w-4" /> Create Model
                </Button>
                <Button variant="outline" onClick={() => setShowCreateForm(false)}>
                  Cancel
                </Button>
              </div>
            </CardContent>
          </Card>
        )}

        {loading ? (
          <div className="text-center py-12 text-slate-500">Loading models...</div>
        ) : models.length === 0 ? (
          <Card className="text-center py-12">
            <CardContent>
              <Zap className="h-12 w-12 text-slate-300 mx-auto mb-4" />
              <h3 className="text-lg font-bold text-slate-700 mb-2">No Model Versions</h3>
              <p className="text-slate-500 mb-4">Create your first AI model version to get started.</p>
              <Button onClick={() => setShowCreateForm(true)} className="gap-2">
                <Plus className="h-4 w-4" /> Create First Model
              </Button>
            </CardContent>
          </Card>
        ) : (
          <div className="space-y-4">
            {models.map(model => (
              <Card key={model.id} className={model.isActive ? 'border-indigo-200 bg-indigo-50/30' : ''}>
                <CardContent className="p-6">
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <div className="flex items-center gap-3 mb-2">
                        <h3 className="text-lg font-bold text-slate-900">{model.versionName}</h3>
                        {model.isActive && (
                          <Badge className="bg-emerald-100 text-emerald-700 border-emerald-200">Active</Badge>
                        )}
                      </div>
                      <p className="text-sm text-slate-600 mb-4 font-mono">{model.modelPath}</p>
                      <div className="flex items-center gap-6">
                        <div className="flex items-center gap-2">
                          <label className="text-sm font-bold text-slate-700">Traffic Weight:</label>
                          <Input
                            type="number"
                            min="0"
                            max="100"
                            className="w-20 h-8"
                            value={model.trafficWeight}
                            onChange={e => handleUpdateWeight(model.id, parseInt(e.target.value) || 0)}
                          />
                          <span className="text-xs text-slate-500">%</span>
                        </div>
                        <div className="text-xs text-slate-400">
                          Created: {new Date(model.createdAt).toLocaleDateString()}
                        </div>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 ml-4">
                      <Button
                        variant={model.isActive ? "outline" : "default"}
                        size="sm"
                        onClick={() => handleToggleActive(model.id, model.isActive)}
                      >
                        {model.isActive ? 'Deactivate' : 'Activate'}
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleDelete(model.id)}
                        className="text-red-600 hover:text-red-700 hover:border-red-200"
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
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
