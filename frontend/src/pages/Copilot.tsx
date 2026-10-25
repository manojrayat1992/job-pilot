import { Card,CardContent,CardHeader,CardTitle } from '@/components/ui/card';
import { Link } from 'react-router-dom';

export default function Copilot() {
  return (
    <Card className="max-w-2xl mx-auto my-12">
      <CardHeader><CardTitle>Browser autofill</CardTitle></CardHeader>
      <CardContent className="space-y-4">
        <p className="text-muted-foreground">Browser autofill is not available yet. You can prepare your resume here and apply directly on the employer’s site.</p>
        <Link className="text-primary underline" to="/dashboard">Back to dashboard</Link>
      </CardContent>
    </Card>
  );
}
