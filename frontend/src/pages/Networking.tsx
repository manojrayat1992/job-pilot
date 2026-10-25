import { Card,CardContent,CardHeader,CardTitle } from '@/components/ui/card';
import { Link } from 'react-router-dom';

export default function Networking() {
  return (
    <Card className="max-w-2xl mx-auto my-12">
      <CardHeader><CardTitle>Contact discovery</CardTitle></CardHeader>
      <CardContent className="space-y-4">
        <p className="text-muted-foreground">Contact discovery is not available yet. We will only provide contacts with verified sources. No credits are charged.</p>
        <Link className="text-primary underline" to="/dashboard">Back to dashboard</Link>
      </CardContent>
    </Card>
  );
}
