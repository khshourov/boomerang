import React from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '@/lib/auth/AuthContext';
import {
  SidebarProvider,
  Sidebar,
  SidebarContent,
  SidebarHeader,
  SidebarFooter,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuButton,
} from '@/components/ui/sidebar';
import { LogOut, Clock } from 'lucide-react';

const DashboardLayout: React.FC = () => {
  const { logout, session } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <SidebarProvider>
      <div className="flex min-h-screen w-full">
        <Sidebar className="border-r">
          <SidebarHeader className="border-b p-4">
            <h1 className="text-xl font-bold">Boomerang</h1>
          </SidebarHeader>
          <SidebarContent>
            <SidebarMenu className="p-2">
              <SidebarMenuItem>
                <SidebarMenuButton asChild>
                  <NavLink
                    to="/tasks"
                    className={({ isActive }) =>
                      `flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                        isActive
                          ? 'bg-primary text-primary-foreground'
                          : 'text-muted-foreground hover:bg-muted'
                      }`
                    }
                  >
                    <Clock className="h-4 w-4" />
                    Tasks
                  </NavLink>
                </SidebarMenuButton>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarContent>
          <SidebarFooter className="border-t p-4">
            <div className="flex flex-col gap-2">
              <div className="text-xs text-muted-foreground truncate">
                Client: {session?.clientId}
              </div>
              <button
                onClick={handleLogout}
                className="flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium text-destructive hover:bg-destructive/10 transition-colors"
              >
                <LogOut className="h-4 w-4" />
                Logout
              </button>
            </div>
          </SidebarFooter>
        </Sidebar>
        <main className="flex-1 overflow-auto bg-muted/20 p-8">
          <Outlet />
        </main>
      </div>
    </SidebarProvider>
  );
};

export default DashboardLayout;
