import React, { forwardRef } from 'react';
import { LucideIcon } from 'lucide-react';
import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

interface FormInputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label: string;
  icon?: LucideIcon;
  error?: string;
}

export const FormInput = forwardRef<HTMLInputElement, FormInputProps>(
  ({ label, icon: Icon, error, className, ...props }, ref) => {
    return (
      <div className="space-y-1.5 w-full">
        <label className="text-sm font-bold text-slate-700 uppercase tracking-widest flex items-center gap-2">
          {Icon && <Icon className="h-4 w-4 text-indigo-500" />}
          {label}
        </label>
        <div className="relative group">
          <input
            ref={ref}
            className={cn(
              "flex h-14 w-full rounded-2xl border border-slate-200 bg-white px-4 py-2 text-base ring-offset-white file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-slate-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 transition-all duration-300",
              error ? "border-red-500 ring-red-500" : "hover:border-indigo-200",
              className
            )}
            {...props}
          />
        </div>
        {error && (
          <p className="text-xs font-bold text-red-500 mt-1 uppercase tracking-tighter">
            {error}
          </p>
        )}
      </div>
    );
  }
);

FormInput.displayName = 'FormInput';
