import { cn } from "@/lib/utils";
import { AnimatePresence,motion } from "framer-motion";
import { ChevronDown } from "lucide-react";
import * as React from "react";

const AccordionContext = React.createContext<{
  activeValue: string | null;
  setActiveValue: (value: string | null) => void;
}>({ activeValue: null, setActiveValue: () => {} });

const Accordion = ({ children, className, defaultValue }: any) => {
  const [activeValue, setActiveValue] = React.useState<string | null>(defaultValue || null);
  return (
    <AccordionContext.Provider value={{ activeValue, setActiveValue }}>
      <div className={cn("space-y-1", className)}>{children}</div>
    </AccordionContext.Provider>
  );
};

const AccordionItem = ({ value, children, className }: any) => {
  const { activeValue } = React.useContext(AccordionContext);
  const isOpen = activeValue === value;

  return (
    <div className={cn("border-b border-slate-100 last:border-0", className)}>
      {React.Children.map(children, child => {
        if (React.isValidElement(child)) {
          // Pass open state down
          return React.cloneElement(child as React.ReactElement<any>, { 
            "data-state": isOpen ? "open" : "closed",
            "data-value": value 
          });
        }
        return child;
      })}
    </div>
  );
};

const AccordionTrigger = ({ className, children, "data-state": state, "data-value": value, ...props }: any) => {
  const { setActiveValue } = React.useContext(AccordionContext);
  const isOpen = state === "open";

  return (
    <button
      type="button"
      onClick={() => setActiveValue(isOpen ? null : value)}
      className={cn(
        "flex w-full items-center justify-between py-4 font-medium transition-all hover:underline [&[data-state=open]>svg]:rotate-180",
        className
      )}
      data-state={state}
      {...props}
    >
      {children}
      <ChevronDown className="h-4 w-4 shrink-0 transition-transform duration-200" />
    </button>
  )
};

const AccordionContent = ({ className, children, "data-state": state, ...props }: any) => {
  const isOpen = state === "open";

  return (
    <AnimatePresence initial={false}>
      {isOpen && (
        <motion.div
          initial={{ height: 0, opacity: 0 }}
          animate={{ height: "auto", opacity: 1 }}
          exit={{ height: 0, opacity: 0 }}
          transition={{ duration: 0.3, ease: "easeInOut" }}
          className="overflow-hidden text-sm"
        >
          <div className={cn("pb-4 pt-0", className)} {...props}>
            {children}
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  )
};

export { Accordion,AccordionContent,AccordionItem,AccordionTrigger };
