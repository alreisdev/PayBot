import { Component } from '@angular/core';
import { ChatPageComponent } from './features/chat/chat-page.component';

/**
 * Root application component
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [ChatPageComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'PayBot';
}
