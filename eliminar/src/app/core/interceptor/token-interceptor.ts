import { HttpInterceptorFn } from '@angular/common/http';
import { Constants } from '../../global/Constants';

export const tokenInterceptor: HttpInterceptorFn = (req, next) => {
  // Excluir la ruta de login
  if (req.url.includes('/login')) {
    return next(req); // No modificar la petición
  }

  const token = sessionStorage.getItem(Constants.tokenKey);
  console.log("tokenInterceptor " + token);
  if (token) {
    req = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(req);
};
