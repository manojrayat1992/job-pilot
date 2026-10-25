import { Card,CardContent,CardHeader,CardTitle } from '@/components/ui/card';
import { Link } from 'react-router-dom';

export default function Pricing() {
  return (
    <Card className="max-w-2xl mx-auto my-12">
      <CardHeader><CardTitle>Paid plans</CardTitle></CardHeader>
      <CardContent className="space-y-4">
        <p className="text-muted-foreground">Paid upgrades are not available yet. You can continue using the current tools within your available credits. No payment will be collected.</p>
        <Link className="text-primary underline" to="/dashboard">Back to dashboard</Link>
      </CardContent>
    </Card>
  );
}
