import React from 'react';
import { Card, CardContent, CardHeader } from '@/components/ui/card';

interface SkeletonLoaderProps {
  count?: number;
  type?: 'card' | 'list' | 'table';
}

export const SkeletonLoader: React.FC<SkeletonLoaderProps> = ({
  count = 3,
  type = 'card',
}) => {
  const CardSkeleton = () => (
    <Card className="border-none shadow-sm">
      <CardHeader className="pb-2">
        <div className="space-y-3">
          <div className="h-4 bg-muted rounded w-3/4 animate-pulse" />
          <div className="h-3 bg-muted rounded w-1/2 animate-pulse" />
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-2">
          <div className="h-3 bg-muted rounded w-full animate-pulse" />
          <div className="h-3 bg-muted rounded w-5/6 animate-pulse" />
          <div className="h-3 bg-muted rounded w-4/6 animate-pulse" />
        </div>
      </CardContent>
    </Card>
  );

  const ListSkeleton = () => (
    <div className="flex items-center gap-4 p-4 border rounded-lg">
      <div className="h-12 w-12 bg-muted rounded-full animate-pulse shrink-0" />
      <div className="flex-1 space-y-2">
        <div className="h-4 bg-muted rounded w-3/4 animate-pulse" />
        <div className="h-3 bg-muted rounded w-1/2 animate-pulse" />
      </div>
    </div>
  );

  const TableSkeleton = () => (
    <div className="flex items-center gap-4 p-4 border-b">
      <div className="h-4 bg-muted rounded w-1/4 animate-pulse" />
      <div className="h-4 bg-muted rounded w-1/4 animate-pulse" />
      <div className="h-4 bg-muted rounded w-1/4 animate-pulse" />
      <div className="h-4 bg-muted rounded w-1/4 animate-pulse" />
    </div>
  );

  if (type === 'list') {
    return (
      <div className="space-y-3">
        {Array.from({ length: count }).map((_, i) => (
          <ListSkeleton key={i} />
        ))}
      </div>
    );
  }

  if (type === 'table') {
    return (
      <div className="border rounded-lg">
        {Array.from({ length: count }).map((_, i) => (
          <TableSkeleton key={i} />
        ))}
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      {Array.from({ length: count }).map((_, i) => (
        <CardSkeleton key={i} />
      ))}
    </div>
  );
};

export default SkeletonLoader;
