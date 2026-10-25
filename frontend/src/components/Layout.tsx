import { Button } from '@/components/ui/button';
import { useAuthStore } from '@/store/authStore';
import { Activity,Bot,Briefcase,CheckSquare,FileText,LayoutDashboard,LogOut,Sparkles,Zap } from 'lucide-react';
import { ReactNode } from 'react';
import { NavLink,useLocation,useNavigate } from 'react-router-dom';

interface LayoutProps {
  children: ReactNode;
}

export default function Layout({ children }: LayoutProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuthStore();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const navItems = [
    { name: 'Dashboard', path: '/dashboard', icon: LayoutDashboard },
    { name: 'Applications', path: '/applications', icon: CheckSquare },
    { name: 'Resumes', path: '/resumes', icon: FileText },
    { name: 'Jobs', path: '/jobs', icon: Briefcase },
    { name: 'Researcher', path: '/ai/job-research', icon: Sparkles },
    { name: 'Agents', path: '/ai/agents', icon: Bot },
    { name: 'Usage', path: '/usage', icon: Activity },
  ];

  return (
    <div className="min-h-screen bg-background flex flex-col">
      <header className="border-b bg-card sticky top-0 z-10 backdrop-blur supports-[backdrop-filter]:bg-card/60">
        <div className="container mx-auto px-4 h-16 flex items-center justify-between">
          <div className="flex items-center gap-2 cursor-pointer" onClick={() => navigate('/dashboard')}>
            <Briefcase className="h-6 w-6 text-primary" />
            <h1 className="text-xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-primary to-primary/60">
              Job Pilot
            </h1>
          </div>
          
          {user?.onboardingCompleted && (
            <nav className="hidden md:flex items-center gap-6">
              {navItems.map((item) => (
                <NavLink
                  key={item.name}
                  to={item.path}
                  className={() =>
                    `text-sm font-medium transition-colors hover:text-primary flex items-center gap-2 ${
                      location.pathname.startsWith(item.path.split('/')[1] ? `/${item.path.split('/')[1]}` : item.path)
                        ? 'text-primary'
                        : 'text-muted-foreground'
                    }`
                  }
                >
                  <item.icon className="h-4 w-4" />
                  {item.name}
                </NavLink>
              ))}
            </nav>
          )}

          <div className="flex items-center gap-4">
            {user?.onboardingCompleted && user?.role !== 'PRO' && (
              <Button 
                variant="default" 
                size="sm" 
                onClick={() => navigate('/pricing')}
                className="hidden md:flex bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-600 hover:to-orange-600 text-white font-bold shadow-sm shadow-amber-500/20"
              >
                <Zap className="h-4 w-4 mr-2" /> Plan availability
              </Button>
            )}
            {user?.onboardingCompleted && (
              <span className="text-sm font-medium hidden sm:inline-block">{user?.name}</span>
            )}
            <Button variant="ghost" size="icon" onClick={handleLogout} title="Logout">
              <LogOut className="h-5 w-5" />
            </Button>
          </div>
        </div>
      </header>

      <main className="flex-1 container mx-auto px-4 py-8">
        {children}
      </main>
    </div>
  );
}
