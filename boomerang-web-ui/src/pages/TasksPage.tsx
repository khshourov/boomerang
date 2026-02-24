import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/api/client';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
  CardDescription,
} from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  DialogFooter,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { useToast } from '@/hooks/use-toast';
import type { ListTasksResponse, TaskRequest, TaskResponse, Task } from '@/types';
import { Plus, Trash2, RefreshCcw, Eye } from 'lucide-react';

const TasksPage: React.FC = () => {
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [viewTask, setViewTask] = useState<Task | null>(null);
  const [formData, setFormData] = useState<TaskRequest>({
    payload: '',
    delayMs: 1000,
    repeatIntervalMs: 0,
  });
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const { data, isLoading, refetch, isRefetching } = useQuery<ListTasksResponse>({
    queryKey: ['tasks'],
    queryFn: async () => {
      const response = await apiClient.get<ListTasksResponse>('/tasks');
      return response.data;
    },
  });

  const registerMutation = useMutation({
    mutationFn: async (newTask: TaskRequest) => {
      // Ensure payload is Base64
      let processedPayload = newTask.payload;
      try {
        // Attempt to decode to see if it's already Base64
        atob(processedPayload);
      } catch (e) {
        // If it fails, it's plain text, so we encode it
        processedPayload = btoa(processedPayload);
      }

      const response = await apiClient.post<TaskResponse>('/tasks', {
        ...newTask,
        payload: processedPayload,
      });
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      setIsDialogOpen(false);
      setFormData({ payload: '', delayMs: 1000, repeatIntervalMs: 0 });
      toast({
        title: 'Task registered',
        description: 'Your task has been successfully scheduled.',
      });
    },
    onError: (error: any) => {
      toast({
        variant: 'destructive',
        title: 'Registration failed',
        description: error.response?.data?.message || 'Failed to register task',
      });
    },
  });

  const cancelMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/tasks/${id}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      toast({
        title: 'Task cancelled',
        description: 'The task has been successfully removed.',
      });
    },
  });

  const handleRegisterSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    registerMutation.mutate(formData);
  };

  const formatDate = (timestamp: number) => {
    return new Date(timestamp).toLocaleString();
  };

  const decodePayload = (base64: string) => {
    try {
      return atob(base64);
    } catch (e) {
      return base64;
    }
  };

  const tasks = data?.tasks || [];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Tasks</h1>
          <p className="text-muted-foreground">Manage your scheduled and recurring tasks.</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="icon" onClick={() => refetch()} disabled={isLoading || isRefetching}>
            <RefreshCcw className={`h-4 w-4 ${isRefetching ? 'animate-spin' : ''}`} />
          </Button>
          <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
            <DialogTrigger asChild>
              <Button>
                <Plus className="mr-2 h-4 w-4" /> Register Task
              </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-[500px]">
              <DialogHeader>
                <DialogTitle>Register New Task</DialogTitle>
                <DialogDescription>
                  Enter the details for your new task.
                </DialogDescription>
              </DialogHeader>
              <form onSubmit={handleRegisterSubmit}>
                <div className="grid gap-4 py-4">
                  <div className="grid gap-2">
                    <Label htmlFor="payload">Payload (Base64 or String)</Label>
                    <Textarea
                      id="payload"
                      value={formData.payload}
                      onChange={(e) => setFormData({ ...formData, payload: e.target.value })}
                      placeholder="Enter task payload..."
                      className="min-h-[100px]"
                      required
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="delay">Delay (milliseconds)</Label>
                    <Input
                      id="delay"
                      type="number"
                      value={formData.delayMs}
                      onChange={(e) => setFormData({ ...formData, delayMs: parseInt(e.target.value) })}
                      min="0"
                      required
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="repeat">Repeat Interval (ms, 0 for one-shot)</Label>
                    <Input
                      id="repeat"
                      type="number"
                      value={formData.repeatIntervalMs}
                      onChange={(e) => setFormData({ ...formData, repeatIntervalMs: parseInt(e.target.value) })}
                      min="0"
                      required
                    />
                  </div>
                </div>
                <DialogFooter>
                  <Button type="submit" disabled={registerMutation.isPending}>
                    {registerMutation.isPending ? 'Registering...' : 'Register Task'}
                  </Button>
                </DialogFooter>
              </form>
            </DialogContent>
          </Dialog>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>All Tasks</CardTitle>
          <CardDescription>
            A list of all tasks scheduled for your client account.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="py-10 text-center text-muted-foreground">Loading tasks...</div>
          ) : tasks.length === 0 ? (
            <div className="py-10 text-center text-muted-foreground">No tasks found. Register one to get started!</div>
          ) : (
            <div className="rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Task ID</TableHead>
                    <TableHead>Expiration</TableHead>
                    <TableHead>Repeat Interval</TableHead>
                    <TableHead className="text-right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {tasks.map((task: Task) => (
                    <TableRow key={task.taskId}>
                      <TableCell className="font-mono text-xs">{task.taskId}</TableCell>
                      <TableCell>{formatDate(task.expirationMs)}</TableCell>
                      <TableCell>
                        {task.repeatIntervalMs > 0 ? (
                          <Badge variant="secondary">{task.repeatIntervalMs}ms</Badge>
                        ) : (
                          'One-shot'
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-2">
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => setViewTask(task)}
                          >
                            <Eye className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="text-destructive hover:bg-destructive/10"
                            onClick={() => cancelMutation.mutate(task.taskId)}
                            disabled={cancelMutation.isPending}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Task Details Dialog */}
      <Dialog open={!!viewTask} onOpenChange={(open) => !open && setViewTask(null)}>
        <DialogContent className="sm:max-w-[500px]">
          <DialogHeader>
            <DialogTitle>Task Details</DialogTitle>
          </DialogHeader>
          {viewTask && (
            <div className="space-y-4 py-4">
              <div className="grid grid-cols-4 gap-2">
                <span className="font-semibold text-sm">Task ID:</span>
                <span className="col-span-3 font-mono text-xs break-all">{viewTask.taskId}</span>
              </div>
              <div className="grid grid-cols-4 gap-2">
                <span className="font-semibold text-sm">Expiration:</span>
                <span className="col-span-3 text-sm">{formatDate(viewTask.expirationMs)}</span>
              </div>
              <div className="grid grid-cols-4 gap-2">
                <span className="font-semibold text-sm">Repeat:</span>
                <span className="col-span-3 text-sm">
                  {viewTask.repeatIntervalMs > 0 ? `${viewTask.repeatIntervalMs}ms` : 'One-shot'}
                </span>
              </div>
              <div className="space-y-2">
                <span className="font-semibold text-sm">Payload (Decoded):</span>
                <div className="p-3 bg-muted rounded-md text-xs font-mono break-all max-h-[200px] overflow-auto">
                  {decodePayload(viewTask.payload)}
                </div>
              </div>
              <div className="space-y-2">
                <span className="font-semibold text-sm">Raw Payload (Base64):</span>
                <div className="p-3 bg-muted/50 rounded-md text-[10px] font-mono break-all max-h-[100px] overflow-auto text-muted-foreground">
                  {viewTask.payload}
                </div>
              </div>
            </div>
          )}
          <DialogFooter>
            <Button onClick={() => setViewTask(null)}>Close</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default TasksPage;
