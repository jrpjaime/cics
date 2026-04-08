import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const AuthenticatedGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if(authService.isAuthenticated()) {
    console.log("return router.navigate(['/home']); AuthenticatedGuard" + authService.isAuthenticated());
    return router.navigate(['/home']);
  }else{
    console.log(" return true; AuthenticatedGuard" + authService.isAuthenticated());
    return true;
  }
};
