import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const token = authService.getAccessToken();

  let authReq = req;
  if (token && !req.url.includes('/auth/login') && !req.url.includes('/auth/tenants')) {
    authReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    });
  }

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !req.url.includes('/auth/refresh') && !req.url.includes('/auth/login')) {
        return authService.refreshToken().pipe(
          switchMap((response) => {
            if (response) {
              const retryReq = req.clone({
                setHeaders: {
                  Authorization: `Bearer ${response.accessToken}`,
                },
              });
              return next(retryReq);
            }
            authService.logout();
            router.navigate(['/login']);
            return throwError(() => error);
          }),
          catchError(() => {
            authService.logout();
            router.navigate(['/login']);
            return throwError(() => error);
          }),
        );
      }
      return throwError(() => error);
    }),
  );
};
