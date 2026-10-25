import { Button } from '@/components/ui/button';
import { Card,CardContent,CardDescription,CardHeader,CardTitle } from '@/components/ui/card';
import { AlertCircle,RefreshCw,XCircle } from 'lucide-react';
import { Component,ReactNode } from 'react';

interface Props {
  children: ReactNode;
  onError?: (error: Error) => void;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ApiErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
    };
  }

  static getDerivedStateFromError(error: Error): State {
    return {
      hasError: true,
      error,
    };
  }

  componentDidCatch(error: Error) {
    console.error('ApiErrorBoundary caught an error:', error);
    this.props.onError?.(error);
  }

  handleRetry = () => {
    this.setState({
      hasError: false,
      error: null,
    });
  };

  handleDismiss = () => {
    this.setState({
      hasError: false,
      error: null,
    });
  };

  render() {
    if (this.state.hasError) {
      return (
        <Card className="border-destructive/50 bg-destructive/5">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-destructive">
              <AlertCircle className="h-5 w-5" />
              API Error
            </CardTitle>
            <CardDescription>
              {this.state.error?.message || 'An error occurred while communicating with the server.'}
            </CardDescription>
          </CardHeader>
          <CardContent className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={this.handleRetry}
            >
              <RefreshCw className="mr-2 h-4 w-4" />
              Retry
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={this.handleDismiss}
            >
              <XCircle className="mr-2 h-4 w-4" />
              Dismiss
            </Button>
          </CardContent>
        </Card>
      );
    }

    return this.props.children;
  }
}

export default ApiErrorBoundary;
