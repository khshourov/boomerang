export interface LoginResponse {
  sessionId: string;
  clientId: string;
}

export interface TaskRequest {
  payload: string;
  delayMs: number;
  repeatIntervalMs: number;
}

export interface TaskResponse {
  id: string;
  status: string;
  message: string;
}

export interface Task {
  taskId: string;
  clientId: string;
  payload: string;
  expirationMs: number;
  repeatIntervalMs: number;
}

export interface ListTasksResponse {
  status: string;
  errorMessage: string;
  tasks: Task[];
  nextToken: string;
}

export interface GetTaskResponse {
  task: Task;
}
